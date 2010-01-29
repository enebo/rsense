package org.cx4a.rsense.ruby;

public enum Visibility {
    PUBLIC, PROTECTED, PRIVATE, MODULE_FUNCTION;
    
    public boolean isPrivate() {
        return this == PRIVATE;
    }
    
    public boolean isProtected() {
        return this == PROTECTED;
    }
}
