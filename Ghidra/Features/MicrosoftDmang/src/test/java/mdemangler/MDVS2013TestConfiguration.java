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
package mdemangler;

/**
 * This class is a derivation of MDBaseTestConfiguration (see javadoc there).  This
 *  class must choose the appropriate truth from MDMangBaseTest (new truths might
 *  need to be added there) and override the appropriate helper methods of
 *  {@link MDBaseTestConfiguration}.  This specific test configuration is for the purpose
 *  of driving the tests of MDMangVS2013Test.
 */
public class MDVS2013TestConfiguration extends MDBaseTestConfiguration {

	public MDVS2013TestConfiguration(boolean quiet) {
		super(quiet);
		mdm = new MDMangVS2013();
	}

	@Override
	protected void setTruth(String mdtruth, String mstruth, String ghtruth, String ms2013truth) {
		if (ms2013truth != null) {
			truth = ms2013truth;
		}
		else {
			truth = mstruth;
		}
	}

	@Override
	protected MDParsableItem doDemangleSymbol(MDMang mdmIn, String mangledIn) throws Exception {
		try {
			return mdmIn.demangle(mangledIn, false); // "false" is different
		}
		catch (MDException e) {
			return null;
		}
	}
}
