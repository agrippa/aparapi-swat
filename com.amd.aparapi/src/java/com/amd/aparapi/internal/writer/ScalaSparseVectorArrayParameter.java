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
       if (writer.multiInput) {
           throw new RuntimeException("No support for SparseVector arrays " +
                   "captured by multi-input lambdas");
       }

       if (BlockWriter.emitOcl) {
           return "__global org_apache_spark_mllib_linalg_SparseVector * restrict " + name +
               ", __global int * restrict " + name + "_indices, __global double * restrict " +
               name + "_values, __global int * restrict " + name +
               "_sizes, __global int * restrict " + name + "_offsets, int n" + name +
               ", int " + name + "_tiling";
       } else {
           return "org_apache_spark_mllib_linalg_SparseVector * " + name +
               ", int * " + name + "_indices, double * " +
               name + "_values, int * " + name +
               "_sizes, int * " + name + "_offsets, int n" + name +
               ", int " + name + "_tiling";
       }
   }

   @Override
   public String getOutputParameterString(KernelWriter writer) {
       if (dir != DIRECTION.OUT) {
           throw new RuntimeException();
       }

       if (BlockWriter.emitOcl) {
           return "__global " + type.replace('.', '_') + "* restrict " + name;
       } else {
           return type.replace('.', '_') + "* " + name;
       }
   }

   @Override
   public String getStructString(KernelWriter writer) {
       return (BlockWriter.emitOcl ? "__global " : "") + "org_apache_spark_mllib_linalg_SparseVector *" +
           name + "; ";
   }

   @Override
   public String getInputInitString(KernelWriter writer, String src) {
       StringBuilder builder = new StringBuilder();
       builder.append("my_" + name + "->values = " + src + "_values + " +
               src + "_offsets[i]; ");
       builder.append("my_" + name + "->indices = " + src + "_indices + " +
               src + "_offsets[i]; ");
       builder.append("my_" + name + "->size = " + src + "_sizes[i];");
       builder.append("my_" + name + "->tiling = " + src + "_tiling;");
       return builder.toString();
   }

   @Override
   public String getGlobalInitString(KernelWriter writer) {
       if (writer.multiInput) {
           throw new RuntimeException("No support for SparseVector arrays " +
                   "captured by multi-input lambdas");
       }

       StringBuilder builder = new StringBuilder();
       builder.append("this_ptr->" + name + " = " + name + ";\n");
       builder.append("   for (int j = 0; j < n" + name + "; j++) {\n");
       builder.append("      (this_ptr->" + name + ")[j].values = " + name +
               "_values + " + name + "_offsets[j];\n");
       builder.append("      (this_ptr->" + name + ")[j].indices = " + name +
               "_indices + " + name + "_offsets[j];\n");
       builder.append("      (this_ptr->" + name + ")[j].size = " + name + "_sizes[j];\n");
       builder.append("      (this_ptr->" + name + ")[j].tiling = " + name + "_tiling;\n");
       builder.append("   }\n");
       return builder.toString();
   }

   @Override
   public Class<?> getClazz() {
       return clazz;
   }
}

