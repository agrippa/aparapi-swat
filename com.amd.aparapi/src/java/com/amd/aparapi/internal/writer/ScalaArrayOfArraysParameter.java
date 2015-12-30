package com.amd.aparapi.internal.writer;

public class ScalaArrayOfArraysParameter extends ScalaArrayParameter {
    private static Class<?> arrayClass;
    static {
        try {
            arrayClass = Class.forName("scala.Array");
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    public final String primitiveElementType;

    public ScalaArrayOfArraysParameter(String type, String name,
            ScalaParameter.DIRECTION dir, String primitiveElementType) {
        super(type, arrayClass, name, dir);
        this.primitiveElementType = primitiveElementType;
    }

    public ScalaArrayOfArraysParameter(String type, Class<?> clazz, String name,
            DIRECTION dir, String primitiveElementType) {
        super(type, clazz, name, dir);
        this.primitiveElementType = primitiveElementType;
    }

    @Override
    public String getInputParameterString(KernelWriter writer) {
        return "__global " + primitiveElementType + " * restrict " + name +
            ", __global int * restrict " + name + "_sizes, " +
            "__global int * restrict " + name + "_offsets, int n" + name;
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.OUT) {
            throw new RuntimeException();
        }

        return "__global int * restrict " + name +
            ", __global int * restrict " + name + "_iters";
    }

    @Override
    public String getInputInitString(KernelWriter writer, String src) {
        return "";
    }

    @Override
    public String getStructString(KernelWriter writer) {
        return "__global " + primitiveElementType + "* " + name;
    }

    @Override
    public String getGlobalInitString(KernelWriter writer) {
        if (dir != DIRECTION.IN) {
            throw new RuntimeException();
        }

        if (writer.multiInput) {
            final StringBuilder builder = new StringBuilder();
            builder.append("this->" + name + " = " + name + " + " + name +
                   "_offsets[i]");
            if (true /* writer.getEntryPoint().getArrayFieldArrayLengthUsed().contains(name) */ ) {
              builder.append("; this->" + name +
                      BlockWriter.arrayLengthMangleSuffix + " = " + name +
                      "_sizes[i]");
            }
            return builder.toString();
        } else {
            throw new UnsupportedOperationException();
        }
    }

}
