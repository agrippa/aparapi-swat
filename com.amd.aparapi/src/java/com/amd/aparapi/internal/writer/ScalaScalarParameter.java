package com.amd.aparapi.internal.writer;

public class ScalaScalarParameter implements ScalaParameter {
    private final String type;
    private final String name;

    public ScalaScalarParameter(String type, String name) {
        if (type.equals("I")) {
            this.type = "int";
        } else if (type.equals("D")) {
            this.type = "double";
        } else if (type.equals("F")) {
            this.type = "float";
        } else {
            throw new RuntimeException(type);
        }
        this.name = name;
    }

    @Override
    public String getInputParameterString(KernelWriter writer) {
        if (writer.multiInput) {
            return "__global " + type + "* restrict " + name;
        } else {
            return type + " " + name;
        }
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInputInitString(KernelWriter writer, String src) {
        return "";
    }

    @Override
    public String getGlobalInitString(KernelWriter writer) {
        if (writer.multiInput) {
            return "this->" + name + " = " + name + "[i]";
        } else {
            return "this->" + name + " = " + name;
        }
    }

    @Override
    public String getStructString(KernelWriter writer) {
        return type + " " + name;
    }

    @Override
    public Class<?> getClazz() { return null; }

    @Override
    public DIRECTION getDir() {
        return ScalaParameter.DIRECTION.IN;
    }
}
