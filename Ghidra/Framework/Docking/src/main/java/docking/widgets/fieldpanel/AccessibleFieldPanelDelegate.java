/* ###
 * IP: GHIDRA
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package docking.widgets.fieldpanel;

import static javax.accessibility.AccessibleContext.*;

import java.awt.IllegalComponentStateException;
import java.awt.Point;
import java.awt.Rectangle;
import java.math.BigInteger;
import java.text.BreakIterator;
import java.util.*;

import javax.accessibility.*;
import javax.swing.Timer;
import javax.swing.text.AttributeSet;

import docking.widgets.EventTrigger;
import docking.widgets.fieldpanel.field.Field;
import docking.widgets.fieldpanel.support.*;

/**
 * Contains all the code for implementing the AccessibleFieldPanel which is an inner class in
 * the FieldPanel class. The AccessibleFieldPanel has to be declared as an inner class because
 * it needs to extend AccessibleJComponent which is a non-static inner class of JComponent. 
 * However, we did not want to put all the logic in there as FieldPanel is already an
 * extremely large and complex class. Also, by delegating the logic, testing is much
 * easier.
 * <P>
 * The model for accessibility for the FieldPanel is a bit complex because
 * the field panel displays text, but in a 2 dimensional array of fields, where each field
 * has potentially 2 dimensional text.  So for the purpose of accessibility, the FieldPanel 
 * acts as both a text field and a text component.
 * <P>
 * To support screen readers reacting to cursor movements in the FieldPanel, the FieldPanel
 * acts like a text field, but it acts like it only has the text of one inner Field at a time
 * (The one where the cursor is). The other approach that was considered was to treat the field
 * panel as a single text document. This would be difficult to implement because of the way fields
 * are multi-lined. Also, the user of the screen reader would lose all concepts that there are
 * fields. By maintaining the fields as a concept to the screen reader, it can provide more
 * meaningful descriptions as the cursor is moved between fields. 
 * <P>
 * The Field panel also acts as an {@link AccessibleComponent} with virtual children for each of its 
 * visible fields. This is what allows screen readers to read the context of whatever the mouse
 * is hovering over keeping the data separated by the field boundaries.
 */
public class AccessibleFieldPanelDelegate {
	private List<AccessibleLayout> accessibleLayouts;
	private int totalFieldCount;
	private AccessibleField[] fieldsCache;
	private FieldPanel panel;
	private javax.swing.Timer setActiveDescendentTimer;

	// caret position tracking
	private FieldLocation cursorLoc;
	private int caretPos;
	private AccessibleLayout accessibleLayout;
	private AccessibleField cursorField;

	private FieldDescriptionProvider fieldDescriber = (l, f) -> "";
	private AccessibleContext context;
	private String description;
	private FieldSelection currentSelection;

	public AccessibleFieldPanelDelegate(List<AnchoredLayout> layouts, AccessibleContext context,
			FieldPanel panel) {
		this.context = context;
		this.panel = panel;
		setLayouts(layouts);
	}

	/**
	 * Whenever the set of visible layouts changes, the field panel rebuilds its info for the
	 * new visible fields and notifies the accessibility system that its children changed.
	 * @param layouts the new set of visible layouts.
	 */
	public void setLayouts(List<AnchoredLayout> layouts) {
		totalFieldCount = 0;
		cursorField = null;
		accessibleLayouts = new ArrayList<>(layouts.size());
		int positionInParent = 0;
		for (AnchoredLayout layout : layouts) {
			AccessibleLayout accessibleLayout =
				new AccessibleLayout(layout, positionInParent++, totalFieldCount);
			accessibleLayouts.add(accessibleLayout);
			totalFieldCount += layout.getNumFields();
		}
		fieldsCache = new AccessibleField[totalFieldCount];
		context.firePropertyChange(ACCESSIBLE_INVALIDATE_CHILDREN, null, panel);
		if (cursorLoc != null) {
			setCaret(cursorLoc, EventTrigger.GUI_ACTION);
		}
	}

	/**
	 * Tells this delegate that the cursor moved. It updates its internal state and fires
	 * events to the accessibility system.
	 * @param newCursorLoc the new FieldLoation of the cursor
	 * @param trigger the event trigger
	 */
	public void setCaret(FieldLocation newCursorLoc, EventTrigger trigger) {
		if (accessibleLayout == null || !isSameLayout(cursorLoc, newCursorLoc)) {
			AccessibleLayout oldLayout = accessibleLayout;
			accessibleLayout = getAccessibleLayout(newCursorLoc.getIndex());
			context.firePropertyChange(ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY, oldLayout,
				accessibleLayout);
		}
		if (accessibleLayout != null) {
			accessibleLayout.setCaret(cursorLoc, newCursorLoc);
		}
		cursorLoc = newCursorLoc;
	}

	/**
	 * Tells this delegate that the selection has changed. If the current field is in the selection,
	 * it sets the current AccessibleField to be selected. (A field is either entirely selected
	 * or not)
	 * @param currentSelection the new current field panel selection
	 * @param trigger the event trigger
	 */
	public void setSelection(FieldSelection currentSelection, EventTrigger trigger) {
		this.currentSelection = currentSelection;
		updateCurrentFieldSelectedState(trigger);
		if (context != null) {
			if (currentSelection.isEmpty()) {
				context.firePropertyChange("AccessibleNotification", null, "Selection cleared");
			}
			else {
				context.firePropertyChange("AccessibleNotification", null, "Selection Changed");
			}

		}
	}

	public void focusGained() {
		if (cursorLoc != null) {
			accessibleLayout = getAccessibleLayout(cursorLoc.getIndex());
			if (accessibleLayout != null) {
				java.awt.event.ActionListener taskPerformer = new java.awt.event.ActionListener() {
					public void actionPerformed(java.awt.event.ActionEvent evt) {
						context.firePropertyChange(ACCESSIBLE_ACTIVE_DESCENDANT_PROPERTY, null,
							accessibleLayout);
					}
				};
				setActiveDescendentTimer = new Timer(2, taskPerformer);
				setActiveDescendentTimer.setRepeats(false);
				setActiveDescendentTimer.start();
			}
		}
	}

	private void updateCurrentFieldSelectedState(EventTrigger trigger) {
		if (cursorField == null) {
			return;
		}
		boolean oldIsSelected = cursorField.isSelected();
		boolean newIsSelected = currentSelection != null && currentSelection.contains(cursorLoc);
		cursorField.setSelected(newIsSelected);
		if (oldIsSelected != newIsSelected && trigger == EventTrigger.GUI_ACTION) {
			context.firePropertyChange(ACCESSIBLE_SELECTION_PROPERTY, null, null);
		}
	}

	private AccessibleTextSequence getAccessibleTextSequence(AccessibleField field) {
		if (field == null) {
			return new AccessibleTextSequence(0, 0, "");
		}
		String text = field.getField().getText();
		return new AccessibleTextSequence(0, text.length(), text);
	}

	/**
	 * Returns the caret position relative the current active field.
	 * @return  the caret position relative the current active field
	 */
	public int getCaretPosition() {
		return caretPos;
	}

	/**
	 * Returns the number of characters in the current active field.
	 * @return the number of characters in the current active field.
	 */
	public int getCharCount() {
		return cursorField != null ? cursorField.getCharCount() : 0;
	}

	private boolean isSameLayout(FieldLocation loc1, FieldLocation loc2) {
		if (loc1 != null && loc2 != null) {
			return loc1.getIndex() == loc2.getIndex();
		}
		return false;
	}

	private boolean isSameField(FieldLocation loc1, FieldLocation loc2) {
		if (loc1.getIndex() != loc2.getIndex()) {
			return false;
		}
		return loc1.getFieldNum() == loc2.getFieldNum();
	}

	/**
	 * Returns the n'th AccessibleField that is visible on the screen.
	 * @param fieldNum the number of the field to get
	 * @return the n'th AccessibleField that is visible on the screen
	 */
	public AccessibleField getAccessibleField(int fieldNum) {
		if (fieldNum < 0 || fieldNum >= fieldsCache.length) {
			return null;
		}
		if (fieldsCache[fieldNum] == null) {
			fieldsCache[fieldNum] = createAccessibleField(fieldNum);
		}
		return fieldsCache[fieldNum];
	}

	public int getAccessibleLayoutCount() {
		return accessibleLayouts.size();
	}

	public AccessibleLayout getAccessibleLayout(int layoutNum) {
		if (layoutNum >= 0 && layoutNum < accessibleLayouts.size())
			return accessibleLayouts.get(layoutNum);
		return null;
	}

	/**
	 * Returns the AccessibleField associated with the given field location.
	 * @param loc the FieldLocation to get the visible field for
	 * @return the AccessibleField associated with the given field location
	 */
	public AccessibleField getAccessibleField(FieldLocation loc) {
		AccessibleLayout accessibleLayout = getAccessibleLayout(loc.getIndex());
		if (accessibleLayout != null) {
			return getAccessibleField(accessibleLayout.getStartingFieldNum() + loc.getFieldNum());
		}

		LayoutModel layoutModel = panel.getLayoutModel();
		Layout layout = layoutModel.getLayout(loc.getIndex());
		if (layout == null) {
			return null;
		}
		Field field = layout.getField(loc.getFieldNum());
		return new AccessibleField(field, panel, loc.getFieldNum(), null);
	}

	private AccessibleLayout getAccessibleLayout(BigInteger index) {
		if (accessibleLayouts == null) {
			return null;
		}
		int result = Collections.binarySearch(accessibleLayouts, index,
			Comparator.comparing(
				o -> o instanceof AccessibleLayout lh ? lh.getIndex() : (BigInteger) o,
				BigInteger::compareTo));

		if (result < 0) {
			return null;
		}
		return accessibleLayouts.get(result);
	}

	private AccessibleField createAccessibleField(int fieldNum) {
		int result = Collections.binarySearch(accessibleLayouts, fieldNum, Comparator
				.comparingInt(o -> o instanceof AccessibleLayout lh ? lh.getStartingFieldNum()
						: (Integer) o));
		if (result < 0) {
			result = -result - 2;
		}
		AccessibleLayout layout = accessibleLayouts.get(result);
		return layout.createAccessibleField(fieldNum);
	}

	/**
	 * Return the bounds relative to the field panel for the character at the given index
	 * @param index the index of the character in the active field whose bounds is to be returned.
	 * @return the bounds relative to the field panel for the character at the given index
	 */
	public Rectangle getCharacterBounds(int index) {
		if (cursorField == null) {
			return null;
		}
		Point loc = cursorField.getLocation();
		Rectangle bounds = cursorField.getCharacterBounds(index);
		bounds.x += loc.x;
		bounds.y += loc.y;
		return bounds;
	}

	/**
	 * Returns the character index at the given point relative to the FieldPanel.
	 * Note this only returns chars in the active field.
	 * @param p the point to get the character for
	 * @return the character index at the given point relative to the FieldPanel.
	 */
	public int getIndexAtPoint(Point p) {
		if (cursorField == null) {
			return 0;
		}
		Rectangle bounds = cursorField.getBounds();
		if (!bounds.contains(p)) {
			return -1;
		}
		Point localPoint = new Point(p.x - bounds.x, p.y - bounds.y);
		return cursorField.getIndexAtPoint(localPoint);
	}

	/**
	 * Returns the char, word, or sentence at the given char index.
	 * @param part  specifies char, word or sentence (See {@link AccessibleText})
	 * @param index the character index to get data for
	 * @return the char, word, or sentences at the given char index
	 */
	public String getAtIndex(int part, int index) {
		if (cursorField == null) {
			return "";
		}
		return cursorField.getAtIndex(part, index);
	}

	/**
	 * Returns the char, word, or sentence after the given char index.
	 * @param part  specifies char, word or sentence (See {@link AccessibleText})
	 * @param index the character index to get data for
	 * @return the char, word, or sentence after the given char index
	 */
	public String getAfterIndex(int part, int index) {
		if (cursorField == null) {
			return "";
		}
		return cursorField.getAfterIndex(part, index);
	}

	/**
	 * Returns the char, word, or sentence at the given char index.
	 * @param part  specifies char, word or sentence (See {@link AccessibleText})
	 * @param index the character index to get data for
	 * @return the char, word, or sentence at the given char index
	 */
	public String getBeforeIndex(int part, int index) {
		if (cursorField == null) {
			return "";
		}
		return cursorField.getBeforeIndex(part, index);
	}

	/**
	 * Returns the number of visible field showing on the screen in the field panel.
	 * @return the number of visible field showing on the screen in the field panel
	 */
	public int getFieldCount() {
		return totalFieldCount;
	}

	/**
	 * Returns the {@link AccessibleField} that is at the given point relative to
	 * the FieldPanel.
	 * 
	 * @param p the point to get an Accessble child at
	 * @return the {@link AccessibleField} that is at the given point relative to
	 *         the FieldPanel
	 */
	public Accessible getAccessibleAt(Point p) {
		int result = Collections.binarySearch(accessibleLayouts, p.y,
			Comparator.comparingInt(
				o -> o instanceof AccessibleLayout lh ? lh.getYpos() : (Integer) o));

		if (result < 0) {
			result = -result - 2;
		}
		if (result < 0 || result >= accessibleLayouts.size()) {
			return null;
		}
		int fieldNum = accessibleLayouts.get(result).getFieldNum(p);
		return getAccessibleField(fieldNum);
	}

	/**
	 * Returns a description of the current field
	 * 
	 * @return a description of the current field
	 */
	public String getFieldDescription() {
		return description;
	}

	/**
	 * Sets the {@link FieldDescriptionProvider} that can generate descriptions of
	 * the current field.
	 * 
	 * @param provider the description provider
	 */
	public void setFieldDescriptionProvider(FieldDescriptionProvider provider) {
		fieldDescriber = provider;
	}

	/**
	 * Returns the selection character start index. This currently always returns 0
	 * as selections are all or nothing.
	 * 
	 * @return the selection character start index.
	 */
	public int getSelectionStart() {
		if (cursorField == null) {
			return 0;
		}
		return cursorField.getSelectionStart();
	}

	/**
	 * Returns the selection character end index. This is either 0, indicating there
	 * is no selection or the index at the end of the text meaning the entire field
	 * is selected.
	 * 
	 * @return the selection character start index.
	 */
	public int getSelectionEnd() {
		if (cursorField == null) {
			return 0;
		}
		return cursorField.getSelectionEnd();
	}

	/**
	 * Returns either null if the field is not selected or the full field text if it
	 * is selected.
	 * 
	 * @return either null if the field is not selected or the full field text if it
	 *         is selected
	 */
	public String getSelectedText() {
		if (cursorField == null) {
			return null;
		}
		return cursorField.getSelectedText();

	}

	/**
	 * Wraps each AnchoredLayout to assist organizing the list of layouts into a
	 * single list of fields.
	 */
	public class AccessibleLayout extends AccessibleContext
			implements Accessible, AccessibleText, AccessibleExtendedText, AccessibleEditableText {

		private AnchoredLayout layout;
		private int positionInParent;
		private int startingFieldNum;

		public AccessibleLayout(AnchoredLayout layout, int positionInParent, int startingFieldNum) {
			this.layout = layout;
			this.positionInParent = positionInParent;
			this.startingFieldNum = startingFieldNum;
		}

		/**
		 * Creates the AccessibleField as needed.
		 * 
		 * @param fieldNum the number of the field to create an AccessibleField for.
		 *                 This number is relative to all the fields in the field panel
		 *                 and not to this layout.
		 * @return an AccessibleField for the given fieldNum
		 */
		public AccessibleField createAccessibleField(int fieldNum) {
			int fieldNumInLayout = fieldNum - startingFieldNum;
			Field field = layout.getField(fieldNumInLayout);
			Rectangle fieldBounds = layout.getFieldBounds(fieldNumInLayout);
			return new AccessibleField(field, panel, fieldNum, fieldBounds);
		}

		/**
		 * Returns the overall field number of the first field in this layout. For
		 * example, the first layout would have a starting field number of 0 and if it
		 * has 5 fields, the next layout would have a starting field number of 5 and so
		 * on.
		 * 
		 * @return the overall field number of the first field in this layout.
		 */
		public int getStartingFieldNum() {
			return startingFieldNum;
		}

		/**
		 * Returns the overall field number of the field containing the given point.
		 * 
		 * @param p the point to find the field for
		 * @return the overall field number of the field containing the given point.
		 */
		public int getFieldNum(Point p) {
			return layout.getFieldIndex(p.x, p.y) + startingFieldNum;
		}

		/**
		 * Return the y position of this layout relative to the field panel.
		 * 
		 * @return the y position of this layout relative to the field panel.
		 */
		public int getYpos() {
			return layout.getYPos();
		}

		/**
		 * Returns the index of the layout as defined by the client code. The only
		 * requirements for indexes is that the index for a layout is always bigger then
		 * the index of the previous layout.
		 * 
		 * @return the index of the layout as defined by the client code.
		 */
		public BigInteger getIndex() {
			return layout.getIndex();
		}

		public void setCaret(FieldLocation oldCursorLoc, FieldLocation newCursorLoc) {
			int oldOffset = -1;
			if (oldCursorLoc != null &&
				oldCursorLoc.getIndex() == newCursorLoc.getIndex()) {
				oldOffset =
					toSimulatedOffset(oldCursorLoc.fieldNum, oldCursorLoc.row, oldCursorLoc.col);
			}
			this.firePropertyChange(ACCESSIBLE_CARET_PROPERTY, oldOffset,
				toSimulatedOffset(newCursorLoc.fieldNum, newCursorLoc.row, newCursorLoc.col));
		}

		@Override
		public AccessibleComponent getAccessibleComponent() {
			return panel.getAccessibleContext().getAccessibleComponent();
		}

		@Override
		public void setTextContents(String s) {
			// TODO Auto-generated method stub

		}

		@Override
		public void insertTextAtIndex(int index, String s) {
			// TODO Auto-generated method stub

		}

		@Override
		public void delete(int startIndex, int endIndex) {
			// TODO Auto-generated method stub

		}

		@Override
		public void cut(int startIndex, int endIndex) {
			// TODO Auto-generated method stub

		}

		@Override
		public void paste(int startIndex) {
			// TODO Auto-generated method stub

		}

		@Override
		public void replaceText(int startIndex, int endIndex, String s) {
			// TODO Auto-generated method stub

		}

		@Override
		public void selectText(int startIndex, int endIndex) {
			// TODO Auto-generated method stub

		}

		@Override
		public void setAttributes(int startIndex, int endIndex, AttributeSet as) {
			// TODO Auto-generated method stub

		}

		@Override
		public String getTextRange(int startIndex, int endIndex) {
			FieldLocation start = fromSimulatedOffset(startIndex);
			FieldLocation end = fromSimulatedOffset(endIndex);
			if (start == null || end == null)
				return "";
			String text = "";
			for (int fieldNum = start.fieldNum; fieldNum <= end.fieldNum; fieldNum++) {
				Field field = layout.getField(fieldNum);
				if (field == null || field.getNumRows() <= start.row)
					continue;
				String fieldText = field.getText();
				if (fieldText == null)
					continue;
				int startOffset = 0;
				if (fieldNum == start.fieldNum)
					startOffset = field.screenLocationToTextOffset(start.row, start.col);
				else
					startOffset = field.screenLocationToTextOffset(start.row, 0);

				int endOffset = Math.min(fieldText.length(),
					field.screenLocationToTextOffset(start.row, field.getNumCols(start.row)));
				if (fieldNum == end.fieldNum)
					if (end.col == 0)
						break;
					else
						endOffset = Math.min(fieldText.length(),
							field.screenLocationToTextOffset(end.row, end.col));
				if (startOffset < endOffset)
					text += fieldText.substring(startOffset, endOffset);
				text += " ";
			}
			return text;
		}

		@Override
		public AccessibleTextSequence getTextSequenceAt(int part, int index) {
			FieldLocation location = fromSimulatedOffset(index);
			Field field = layout.getField(location.fieldNum);
			if (field == null)
				return null;
			var text = field.getText();
			if (text == null && part != AccessibleExtendedText.LINE)
				return null;
			switch (part) {
				case AccessibleText.CHARACTER: {
					int offset = field.screenLocationToTextOffset(location.row, location.col);
					if (offset >= text.length())
						return null;
					return new AccessibleTextSequence(index, index + 1,
						text.substring(offset, offset + 1));
				}

				case AccessibleText.WORD: {
					return getWordSequence(field, location, text);
				}
				case AccessibleExtendedText.ATTRIBUTE_RUN:
				case AccessibleExtendedText.LINE: {
					int firstFieldNum = layout.getBeginRowFieldNum(location.fieldNum);
					Field firstField = layout.getField(firstFieldNum);
					int lastFieldNum = layout.getEndRowFieldNum(firstFieldNum);
					if (firstFieldNum == lastFieldNum)
						return null;
					Field lastField = null;
					while (lastFieldNum > firstFieldNum) {
						Field testField = layout.getField(lastFieldNum - 1);
						if (location.row < testField.getNumRows()) {
							lastField = testField;
							break;
						}

						lastFieldNum--;
					}
					if (lastField == null)
						return null;
					int simulatedStart = toSimulatedOffset(firstFieldNum, location.row, 0);
					int simulatedEnd = toSimulatedOffset(lastFieldNum - 1, location.row,
						lastField.getNumCols(location.row));
					return new AccessibleTextSequence(simulatedStart, simulatedEnd, "dummy");
				}
				default:
					break;
			}
			return null;
		}

		@Override
		public AccessibleTextSequence getTextSequenceAfter(int part, int index) {
			AccessibleTextSequence sequence = getTextSequenceAt(part, index);
			if (sequence == null)
				return null;
			FieldLocation location = fromSimulatedOffset(index);

			switch (part) {
				case AccessibleText.WORD: {
					int simulatedOffset = toSimulatedOffset(location.fieldNum + 1, location.row, 0);
					return new AccessibleTextSequence(simulatedOffset, simulatedOffset + 1, "");
				}
				case AccessibleText.CHARACTER:
					return new AccessibleTextSequence(sequence.startIndex + 1,
						sequence.startIndex + 2, "dummy");
				default:
					break;
			}

			return null;
		}

		@Override
		public AccessibleTextSequence getTextSequenceBefore(int part, int index) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public Rectangle getTextBounds(int startIndex, int endIndex) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getIndexAtPoint(Point p) {
			// TODO Auto-generated method stub
			return 0;
		}

		@Override
		public Rectangle getCharacterBounds(int i) {
			FieldLocation location = fromSimulatedOffset(i);
			if (location == null)
				return null;

			Field field = layout.getField(location.fieldNum);
			if (field != null) {
				Rectangle bounds = field.getCursorBounds(location.row, location.col);
				if (bounds != null)
					bounds.y += layout.getYPos();
				return bounds;
			}
			return null;
		}

		@Override
		public int getCharCount() {
			// needs work
			return 256;
		}

		@Override
		public int getCaretPosition() {
			FieldLocation position = panel.getCursorLocation();
			return toSimulatedOffset(position.fieldNum, position.row, position.col);
		}

		@Override
		public String getAtIndex(int part, int index) {
			AccessibleTextSequence sequence = getTextSequenceAt(part, index);
			return (sequence == null) ? null : sequence.text;
		}

		@Override
		public String getAfterIndex(int part, int index) {
			AccessibleTextSequence sequence = getTextSequenceAfter(part, index);
			return (sequence == null) ? null : sequence.text;
		}

		@Override
		public String getBeforeIndex(int part, int index) {
			AccessibleTextSequence sequence = getTextSequenceBefore(part, index);
			return (sequence == null) ? null : sequence.text;
		}

		@Override
		public AttributeSet getCharacterAttribute(int i) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public int getSelectionStart() {
			return getCaretPosition();
		}

		@Override
		public int getSelectionEnd() {
			return getCaretPosition();
		}

		@Override
		public String getSelectedText() {
			// TODO Auto-generated method stub
			return null;
		}

		int toSimulatedOffset(int fieldNum, int row, int col) {
			if (layout == null)
				return 0;
			int fieldCount = layout.getNumFields();
			if (fieldCount == 0)
				return 0;
			int accumulatedLength = 0;
			for (int rowIdx = 0; rowIdx <= row; rowIdx++) {
				for (int fieldIdx = 0; fieldIdx < fieldCount; fieldIdx++) {
					Field field = layout.getField(fieldIdx);
					if (field == null || rowIdx >= field.getNumRows())
						continue;
					if (row == rowIdx && fieldNum == fieldIdx) {
						accumulatedLength += col;
						return accumulatedLength;
					}
					accumulatedLength += field.getNumCols(rowIdx) + 1;
				}
			}
			return accumulatedLength;
		}

		FieldLocation fromSimulatedOffset(int offset) {
			if (layout == null)
				return null;
			int fieldCount = layout.getNumFields();
			if (fieldCount == 0)
				return null;
			int accumulatedLength = 0;
			int lastValidRowIdx = 0;
			for (int rowIdx = 0; rowIdx <= 5; rowIdx++) {
				for (int fieldIdx = 0; fieldIdx < fieldCount; fieldIdx++) {
					Field field = layout.getField(fieldIdx);
					if (field == null || rowIdx >= field.getNumRows())
						continue;
					lastValidRowIdx = rowIdx;
					if (offset <= accumulatedLength + field.getNumCols(rowIdx)) {
						return new FieldLocation(layout.getIndex(), fieldIdx, rowIdx,
							offset - accumulatedLength);
					}
					accumulatedLength += field.getNumCols(rowIdx) + 1;
				}
			}
			return new FieldLocation(layout.getIndex(), fieldCount - 1, lastValidRowIdx, 99);
		}

		@Override
		public AccessibleContext getAccessibleContext() {
			return this;
		}

		@Override
		public AccessibleRole getAccessibleRole() {
			return AccessibleRole.TEXT;
		}

		@Override
		public AccessibleStateSet getAccessibleStateSet() {
			AccessibleStateSet accessibleStateSet = new AccessibleStateSet();
			accessibleStateSet.add(AccessibleState.EDITABLE);
			accessibleStateSet.add(AccessibleState.MULTI_LINE);
			accessibleStateSet.add(AccessibleState.ENABLED);
			return accessibleStateSet;
		}

		@Override
		public Accessible getAccessibleParent() {
			return panel;
		}

		@Override
		public AccessibleText getAccessibleText() {
			return this;
		}

		@Override
		public AccessibleEditableText getAccessibleEditableText() {
			return this;
		}

		@Override
		public int getAccessibleIndexInParent() {
			return positionInParent;
		}

		@Override
		public int getAccessibleChildrenCount() {
			return 0;
		}

		@Override
		public Accessible getAccessibleChild(int i) {
			return null;
		}

		@Override
		public Locale getLocale() throws IllegalComponentStateException {
			// TODO Auto-generated method stub
			return null;
		}

		AccessibleTextSequence getWordSequence(Field field, FieldLocation location,
				String fieldText) {
			if (field.getNumCols(location.row) == 0) {
				return null;
			}
			final int rowStartOffset = field.screenLocationToTextOffset(location.row, 0);
			final int rowLength = field.getNumCols(location.row);
			String text = fieldText.substring(rowStartOffset,
				Math.min(rowStartOffset + rowLength, fieldText.length()));
			final int offset =
				field.screenLocationToTextOffset(location.row, location.col) - rowStartOffset;
			if (offset >= rowLength ||
				offset >= text.length() ||
				Character.isWhitespace(text.charAt(offset))) {
				return new AccessibleTextSequence(offset, offset + 1, " ");
			}

			int wordStart = offset;
			int wordEnd = offset + 1;
			BreakIterator iterator = BreakIterator.getWordInstance(panel.getLocale());
			iterator.setText(text);
			if (!iterator.isBoundary(offset)) {
				wordStart = iterator.preceding(offset);
				if (wordStart == BreakIterator.DONE)
					wordStart = 0;
			}
			wordEnd = iterator.following(wordStart);
			if (wordEnd == BreakIterator.DONE)
				wordEnd = text.length();
			return new AccessibleTextSequence(
				toSimulatedOffset(location.fieldNum, location.row, wordStart),
				toSimulatedOffset(location.fieldNum, location.row, wordEnd),
				text.substring(wordStart, wordEnd));
		}
	}
}
