package com.amd.aparapi.internal.writer;

public class ScalaSparseVectorArrayParameter extends ScalaArrayParameter {
    private static Class<?> sparseVectorClass;
    static {
        try {
            sparseVectorClass = Class.forName("org.apache.spark.mllib.linalg.SparseVector");
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

   public ScalaSparseVectorArrayParameter(String type, String name,
           ScalaParameter.DIRECTION dir) {
       super(type, sparseVectorClass, name, dir);
   }

    public ScalaSparseVectorArrayParameter(String type, Class<?> clazz, String name,
            DIRECTION dir) {
        super(type, clazz, name, dir);
    }

   @Override
   public String getInputParameterString(KernelWriter writer) {
       return "__global org_apache_spark_mllib_linalg_SparseVector* " + name +
           ", __global int *" + name + "_indices, __global double *" +
           name + "_values, __global int *" + name +
           "_sizes, __global int *" + name + "_offsets, int n" + name;
   }

   @Override
   public String getOutputParameterString(KernelWriter writer) {
       if (dir != DIRECTION.OUT) {
           throw new RuntimeException();
       }

       return "__global " + type.replace('.', '_') + "* " + name;
   }

   @Override
   public String getStructString(KernelWriter writer) {
       return "__global org_apache_spark_mllib_linalg_SparseVector *" + name + "; ";
   }

   @Override
   public String getInputInitString(KernelWriter writer, String src) {
       StringBuilder builder = new StringBuilder();
       builder.append("my_" + name + "->values = " + src + "_values + " +
               src + "_offsets[i]; ");
       builder.append("my_" + name + "->indices = " + src + "_indices + " +
               src + "_offsets[i]; ");
       builder.append("my_" + name + "->size = " + src + "_sizes[i];");
       return builder.toString();
   }

   @Override
   public String getGlobalInitString(KernelWriter writer) {
       StringBuilder builder = new StringBuilder();
       builder.append("this->" + name + " = " + name + ";\n");
       builder.append("   for (int j = 0; j < n" + name + "; j++) {\n");
       builder.append("      (this->" + name + ")[j].values = " + name +
               "_values + " + name + "_offsets[j];\n");
       builder.append("      (this->" + name + ")[j].indices = " + name +
               "_indices + " + name + "_offsets[j];\n");
       builder.append("      (this->" + name + ")[j].size = " + name + "_sizes[j];\n");
       builder.append("   }\n");
       return builder.toString();
   }

   @Override
   public Class<?> getClazz() {
       return clazz;
   }
}

