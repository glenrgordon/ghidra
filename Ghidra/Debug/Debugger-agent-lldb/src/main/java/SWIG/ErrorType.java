/* ###
 * IP: Apache License 2.0 with LLVM Exceptions
 */
package SWIG;


/* ----------------------------------------------------------------------------
 * This file was automatically generated by SWIG (http://www.swig.org).
 * Version 4.0.2
 *
 * Do not make changes to this file unless you know what you are doing--modify
 * the SWIG interface file instead.
 * ----------------------------------------------------------------------------- */


public final class ErrorType {
  public final static ErrorType eErrorTypeInvalid = new ErrorType("eErrorTypeInvalid");
  public final static ErrorType eErrorTypeGeneric = new ErrorType("eErrorTypeGeneric");
  public final static ErrorType eErrorTypeMachKernel = new ErrorType("eErrorTypeMachKernel");
  public final static ErrorType eErrorTypePOSIX = new ErrorType("eErrorTypePOSIX");
  public final static ErrorType eErrorTypeExpression = new ErrorType("eErrorTypeExpression");
  public final static ErrorType eErrorTypeWin32 = new ErrorType("eErrorTypeWin32");

  public final int swigValue() {
    return swigValue;
  }

  public String toString() {
    return swigName;
  }

  public static ErrorType swigToEnum(int swigValue) {
    if (swigValue < swigValues.length && swigValue >= 0 && swigValues[swigValue].swigValue == swigValue)
      return swigValues[swigValue];
    for (int i = 0; i < swigValues.length; i++)
      if (swigValues[i].swigValue == swigValue)
        return swigValues[i];
    throw new IllegalArgumentException("No enum " + ErrorType.class + " with value " + swigValue);
  }

  private ErrorType(String swigName) {
    this.swigName = swigName;
    this.swigValue = swigNext++;
  }

  private ErrorType(String swigName, int swigValue) {
    this.swigName = swigName;
    this.swigValue = swigValue;
    swigNext = swigValue+1;
  }

  private ErrorType(String swigName, ErrorType swigEnum) {
    this.swigName = swigName;
    this.swigValue = swigEnum.swigValue;
    swigNext = this.swigValue+1;
  }

  private static ErrorType[] swigValues = { eErrorTypeInvalid, eErrorTypeGeneric, eErrorTypeMachKernel, eErrorTypePOSIX, eErrorTypeExpression, eErrorTypeWin32 };
  private static int swigNext = 0;
  private final int swigValue;
  private final String swigName;
}

