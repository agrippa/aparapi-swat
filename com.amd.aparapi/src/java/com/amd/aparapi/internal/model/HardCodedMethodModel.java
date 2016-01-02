package com.amd.aparapi.internal.model;

import com.amd.aparapi.internal.writer.KernelWriter;

public class HardCodedMethodModel extends MethodModel {
    private final String name;
    private final String sig;
    private final MethodDefGenerator methodDef;
    private final String getterFieldName;
    private String ownerMangledName;

    public HardCodedMethodModel(String name, String sig,
            MethodDefGenerator methodDef, boolean isGetter, String getterFieldName) {
        this.name = name;
        this.sig = sig;
        this.methodDef = methodDef;
        this.methodIsGetter = isGetter;
        this.getterFieldName = getterFieldName;
    }

    public void setOwnerMangledName(String s) {
        this.ownerMangledName = s;
    }

    public String getOriginalName() {
        return name;
    }

    @Override
    public String getName() {
        return getOwnerClassMangledName() + "__" + name.replace('<', '_').replace('>', '_');
    }

    @Override
    public String getDescriptor() {
        return sig;
    }

    public String getMethodDef(HardCodedClassModel classModel, KernelWriter writer) {
        StringBuilder sb = new StringBuilder();
        final String returnType = methodDef.getMethodReturnType(this,
                classModel, writer);
        final String methodName = methodDef.getMethodName(this, classModel,
                writer);
        final String args = methodDef.getMethodArgs(this, classModel, writer);
        sb.append("static " + returnType + " " + methodName + "(");
        if (writer.getEntryPoint().requiresHeap()) {
            sb.append(KernelWriter.functionArgumentsPrefix);
            if (args.length() > 0) sb.append(", ");
        }
        sb.append(args + ") {\n");
        sb.append(methodDef.getMethodBody(this, classModel, writer));
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public String getGetterField() {
        if (methodIsGetter) {
            return getterFieldName;
        } else {
            return null;
        }
    }

    @Override
    public String getOwnerClassMangledName() {
        return ownerMangledName;
    }

    public abstract static class MethodDefGenerator<T extends HardCodedClassModel> {
        public abstract String getMethodReturnType(HardCodedMethodModel method,
            T classModel, KernelWriter writer);
        public abstract String getMethodName(HardCodedMethodModel method,
            T classModel, KernelWriter writer);
        public abstract String getMethodArgs(HardCodedMethodModel method,
            T classModel, KernelWriter writer);
        public abstract String getMethodBody(HardCodedMethodModel method,
            T classModel, KernelWriter writer);
    }
}
