package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;
import com.amd.aparapi.internal.model.HardCodedMethodModel.MethodDefGenerator;
import com.amd.aparapi.internal.writer.KernelWriter;
import com.amd.aparapi.internal.writer.BlockWriter;

public class DenseVectorClassModel extends HardCodedClassModel {
    private static final String className = "org.apache.spark.mllib.linalg.DenseVector";
    private static Class<?> clz;
    static {
        try {
            clz = Class.forName(className);
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    private DenseVectorClassModel(List<HardCodedMethodModel> methods, List<AllFieldInfo> fields) {
        super(clz, methods, fields);
        initMethodOwners();
    }

    @Override
    public String getDescriptor() {
        return "Lorg/apache/spark/mllib/linalg/DenseVector";
    }

    @Override
    public boolean merge(HardCodedClassModel other) {
        return (other instanceof DenseVectorClassModel);
    }

    public static DenseVectorClassModel create() {
        List<HardCodedMethodModel> methods = new ArrayList<HardCodedMethodModel>();

        MethodDefGenerator sizeGen = new MethodDefGenerator<DenseVectorClassModel>() {
            @Override
            public String getMethodReturnType(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                return "int";
            }

            @Override
            public String getMethodName(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                return method.getName();
            }

            @Override
            public String getMethodArgs(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();
                return (BlockWriter.emitOcl ? "__global " : "") + owner + " *this_ptr";
            }

            @Override
            public String getMethodBody(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                return "    return (this_ptr->size);\n";
            }
        };
        methods.add(new HardCodedMethodModel("size", "()I", sizeGen, false, null));

        MethodDefGenerator applyGen = new MethodDefGenerator<DenseVectorClassModel>() {
            @Override
            public String getMethodReturnType(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                return "double";
            }

            @Override
            public String getMethodName(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                return method.getName();
            }

            @Override
            public String getMethodArgs(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();
                return (BlockWriter.emitOcl ? "__global " : "") + owner + " *this_ptr, int index";
            }

            @Override
            public String getMethodBody(HardCodedMethodModel method,
                    DenseVectorClassModel classModel, KernelWriter writer) {
                return "    return (this_ptr->values)[this_ptr->tiling * index];\n";
            }
        };
        methods.add(new HardCodedMethodModel("apply", "(I)D", applyGen, false, null));

        List<AllFieldInfo> fields = new ArrayList<AllFieldInfo>(2);
        fields.add(new AllFieldInfo("values", "[D", "double*", -1));
        fields.add(new AllFieldInfo("size", "I", "int", -1));
        fields.add(new AllFieldInfo("tiling", "I", "int", -1));

        return new DenseVectorClassModel(methods, fields);
    }

    @Override
    public List<String> getNestedTypeDescs() {
        return new ArrayList<String>(0);
    }

    @Override
    public String getMangledClassName() {
        return className.replace('.', '_').replace('$', '_');
    }

   @Override
   public boolean classNameMatches(String className) {
      return className.startsWith(className);
   }

   @Override
   public int calcTotalStructSize(Entrypoint entryPoint) {
       /*
        * Size of the pointer to values + size of the integer size + size of the
        * integer tiling
        */
       final int pointerSize = Integer.parseInt(entryPoint.getConfig().get(
                   Entrypoint.clDevicePointerSize));
       if (BlockWriter.emitOcl) {
           return (pointerSize + 4 + 4);
       } else {
           return KernelWriter.roundUpToAlignment(pointerSize + 4 + 4);
       }
   }

   @Override
   public String toString() {
       return className;
   }
}
