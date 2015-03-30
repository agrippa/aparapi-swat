package com.amd.aparapi.internal.model;

public class HardCodedMethodModel extends MethodModel {
    private final String name;
    private final String sig;
    private final String methodDef;
    private final String getterFieldName;

    public HardCodedMethodModel(String name, String sig, String methodDef, boolean isGetter, String getterFieldName) {
        this.name = name;
        this.sig = sig;
        this.methodDef = methodDef;
        this.methodIsGetter = isGetter;
        this.getterFieldName = getterFieldName;
    }

    public String getName() {
        return name;
    }

    @Override
    public String getDescriptor() {
        return sig;
    }

    public String getMethodDef() {
        return methodDef;
    }

    @Override
    public String getGetterField() {
        if (methodIsGetter) {
            return getterFieldName;
        } else {
            return null;
        }
    }
}
