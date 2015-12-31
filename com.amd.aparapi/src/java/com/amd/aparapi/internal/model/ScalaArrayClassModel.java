package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;
import com.amd.aparapi.internal.model.HardCodedMethodModel.MethodDefGenerator;
import com.amd.aparapi.internal.writer.KernelWriter;

public class ScalaArrayClassModel extends HardCodedClassModel {
    private static final String className = "scala.Array";
    private static Class<?> clz;
    static {
        try {
            clz = Class.forName(className);
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    private final String eleDesc;

    private ScalaArrayClassModel(List<HardCodedMethodModel> methods,
            List<AllFieldInfo> fields, String eleDesc) {
        super(clz, methods, fields);
        initMethodOwners();
        this.eleDesc = eleDesc;
    }

    @Override
    public String getDescriptor() {
        return "[" + eleDesc;
    }

    @Override
    public boolean merge(HardCodedClassModel other) {
        return other instanceof ScalaArrayClassModel && this.eleDesc.equals(((ScalaArrayClassModel)other).eleDesc);
    }

    public static ScalaArrayClassModel create(String eleDesc) {
        List<HardCodedMethodModel> methods = new ArrayList<HardCodedMethodModel>();

        final String typeName;
        if (eleDesc.equals("I")) {
            typeName = "int";
        } else if (eleDesc.equals("F")) {
            typeName = "float";
        } else if (eleDesc.equals("D")) {
            typeName = "double";
        } else if (eleDesc.equals("B")) {
            typeName = "char";
        } else {
            throw new RuntimeException("eleDesc = " + eleDesc);
        }

        MethodDefGenerator sizeGen = new MethodDefGenerator<ScalaArrayClassModel>() {
            @Override
            public String getMethodReturnType(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                return "int";
            }

            @Override
            public String getMethodName(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                return method.getName();
            }

            @Override
            public String getMethodArgs(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();
                return "__global " + owner + " *this";
            }

            @Override
            public String getMethodBody(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                return "    return (this->size);\n";
            }
        };
        methods.add(new HardCodedMethodModel("size", "()I", sizeGen, false, null));

        MethodDefGenerator applyGen = new MethodDefGenerator<ScalaArrayClassModel>() {
            @Override
            public String getMethodReturnType(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                return typeName;
            }

            @Override
            public String getMethodName(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                return method.getName();
            }

            @Override
            public String getMethodArgs(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();
                return "__global " + owner + " *this, int index";
            }

            @Override
            public String getMethodBody(HardCodedMethodModel method,
                    ScalaArrayClassModel classModel, KernelWriter writer) {
                return "    return (this->values)[this->tiling * index];\n";
            }
        };
        methods.add(new HardCodedMethodModel("apply", "(I)" + eleDesc, applyGen, false, null));

        List<AllFieldInfo> fields = new ArrayList<AllFieldInfo>(2);
        fields.add(new AllFieldInfo("values", "[" + eleDesc, typeName + "*", -1));
        fields.add(new AllFieldInfo("size", "I", "int", -1));
        fields.add(new AllFieldInfo("tiling", "I", "int", -1));

        return new ScalaArrayClassModel(methods, fields, eleDesc);
    }

    @Override
    public List<String> getNestedTypeDescs() {
        return new ArrayList<String>(0);
    }

    @Override
    public String getMangledClassName() {
        return className.replace('.', '_');
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
       return (pointerSize + 4 + 4);
   }

   @Override
   public String toString() {
       return className;
   }
}
