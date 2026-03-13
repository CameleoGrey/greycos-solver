package ai.greycos.solver.core.impl.cotwin.common.accessor;

public enum MemberAccessorType {
  FIELD_OR_READ_METHOD,
  FIELD_OR_READ_METHOD_WITH_OPTIONAL_PARAMETER,
  FIELD_OR_GETTER_METHOD,
  FIELD_OR_GETTER_METHOD_WITH_SETTER(true),
  VOID_METHOD;

  private final boolean setterRequired;

  MemberAccessorType() {
    this(false);
  }

  MemberAccessorType(boolean setterRequired) {
    this.setterRequired = setterRequired;
  }

  public boolean isSetterRequired() {
    return setterRequired;
  }
}
