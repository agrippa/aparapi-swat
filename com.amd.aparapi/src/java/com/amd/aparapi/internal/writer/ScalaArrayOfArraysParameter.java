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
            ScalaParameter.DIRECTION dir) {
        super(type, arrayClass, name, dir);
        // type = "double []"
        primitiveElementType = type.split(" ")[0];
    }

    public ScalaArrayOfArraysParameter(String type, Class<?> clazz, String name,
            DIRECTION dir) {
        super(type, clazz, name, dir);
        // type = "double []"
        primitiveElementType = type.split(" ")[0];
    }

    @Override
    public String getInputParameterString(KernelWriter writer) {
        if (writer.multiInput) {
            throw new RuntimeException("No support for non-multi-input array inputs");
        }
        return "__global scala_Array * restrict " + name +
            ", __global " + primitiveElementType + " * restrict " +
            name + "_values, __global int * restrict " + name +
            "_sizes, __global int * restrict " + name + "_offsets, int n" + name +
            ", int " + name + "_tiling";
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getInputInitString(KernelWriter writer, String src) {
        return "my_" + name + "->values = " + src + "_values + " + src +
            "_offsets[i]; my_" + name + "->size = " + src + "_sizes[i]; my_" +
            name + "->tiling = " + src + "_tiling;";
    }

    @Override
    public String getStructString(KernelWriter writer) {
        return "__global scala_Array *" + name + "; ";
    }

    @Override
    public String getGlobalInitString(KernelWriter writer) {
        if (writer.multiInput) {
            // TODO
            StringBuilder builder = new StringBuilder();
            builder.append("this->" + name + " = " + name + ";\n");
            builder.append("   for (int j = 0; j < n" + name + "; j++) {\n");
            builder.append("      (this->" + name + ")[j].values = " + name +
                    "_values + " + name + "_offsets[j];\n");
            builder.append("      (this->" + name + ")[j].size = " + name +
                    "_sizes[j];\n");
            builder.append("      (this->" + name + ")[j].tiling = " + name +
                    "_tiling;\n");
            builder.append("   }\n");
            return builder.toString();
        } else {
            throw new RuntimeException("No support for arrays of arrays as a " + 
                    "normal input");
        }
    }
}
