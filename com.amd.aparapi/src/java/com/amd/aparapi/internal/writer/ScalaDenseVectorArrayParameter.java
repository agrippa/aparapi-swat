package com.amd.aparapi.internal.writer;

public class ScalaDenseVectorArrayParameter extends ScalaArrayParameter {
    private static Class<?> denseVectorClass;
    static {
        try {
            denseVectorClass = Class.forName("org.apache.spark.mllib.linalg.DenseVector");
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    public ScalaDenseVectorArrayParameter(String type, String name,
            ScalaParameter.DIRECTION dir) {
        super(type, denseVectorClass, name, dir);
    }

    public ScalaDenseVectorArrayParameter(String type, Class<?> clazz, String name,
            DIRECTION dir) {
        super(type, clazz, name, dir);
    }

    @Override
    public String getInputParameterString(KernelWriter writer) {
        if (writer.multiInput) {
            throw new RuntimeException("No support for DenseVector arrays " +
                    "captured by multi-input lambdas");
        }

        return "__global org_apache_spark_mllib_linalg_DenseVector * restrict " + name +
            ", __global double * restrict " + name + "_values, __global int * restrict " +
            name + "_sizes, __global int * restrict " + name + "_offsets, int n" + name +
            ", int " + name + "_tiling";
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.OUT) {
            throw new RuntimeException();
        }

        return "__global " + type.replace('.', '_') + "* restrict " + name;
    }

    @Override
    public String getInputInitString(KernelWriter writer, String src) {
        return "my_" + name + "->values = " + src + "_values + " + src +
            "_offsets[i]; my_" + name + "->size = " + src + "_sizes[i]; my_" +
            name + "->tiling = " + src + "_tiling;";
    }

    @Override
    public String getStructString(KernelWriter writer) {
        return "__global org_apache_spark_mllib_linalg_DenseVector *" + name + "; ";
    }

    @Override
    public String getGlobalInitString(KernelWriter writer) {
        if (writer.multiInput) {
            throw new RuntimeException("No support for DenseVector arrays " +
                    "captured by multi-input lambdas");
        }

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
    }
}
