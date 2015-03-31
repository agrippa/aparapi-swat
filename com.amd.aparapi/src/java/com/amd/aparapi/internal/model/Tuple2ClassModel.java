package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;
import com.amd.aparapi.internal.model.HardCodedMethodModel.MethodDefGenerator;
import com.amd.aparapi.internal.writer.KernelWriter;

public class Tuple2ClassModel extends HardCodedClassModel {
    private final String firstTypeDesc;
    private final String secondTypeDesc;
    private final String firstTypeClassName;
    private final String secondTypeClassName;

    private static Class<?> clz;
    static {
        try {
            clz = Class.forName("scala.Tuple2");
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    private Tuple2ClassModel(String firstTypeDesc, String secondTypeDesc,
            String firstTypeClassName, String secondTypeClassName,
            List<HardCodedMethodModel> methods, List<AllFieldInfo> fields) {
        super(clz, methods, fields);
        this.firstTypeDesc = firstTypeDesc;
        this.secondTypeDesc = secondTypeDesc;
        this.firstTypeClassName = firstTypeClassName;
        this.secondTypeClassName = secondTypeClassName;
        initMethodOwners();
    }

    public String getFirstTypeDesc() {
      return firstTypeDesc;
    }

    public String getSecondTypeDesc() {
      return secondTypeDesc;
    }

    public String getFirstTypeClassName() {
      return firstTypeClassName;
    }

    public String getSecondTypeClassName() {
      return secondTypeClassName;
    }

    public static Tuple2ClassModel create(String firstTypeDesc,
            String firstTypeClassName, String secondTypeDesc,
            String secondTypeClassName) {

        List<AllFieldInfo> fields = new ArrayList<AllFieldInfo>(2);
        fields.add(new AllFieldInfo("_1", firstTypeDesc, firstTypeClassName, -1));
        fields.add(new AllFieldInfo("_2", secondTypeDesc, secondTypeClassName, -1));

        MethodDefGenerator constructorGen = new MethodDefGenerator<Tuple2ClassModel>() {
            @Override
            public String getMethodDef(HardCodedMethodModel method,
                    Tuple2ClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();

                String firstType = writer.convertType(classModel.getFirstTypeDesc(), true);
                if (classModel.getFirstTypeDesc().startsWith("L")) {
                  ClassModel cm = writer.getEntryPoint().getObjectArrayFieldsClasses().get(
                      firstType.trim());
                  firstType = "__global " + cm.getMangledClassName() + " * ";
                }

                String secondType = writer.convertType(classModel.getSecondTypeDesc(), true);
                if (classModel.getSecondTypeDesc().startsWith("L")) {
                  ClassModel cm = writer.getEntryPoint().getObjectArrayFieldsClasses().get(
                      secondType.trim());
                  secondType = "__global " + cm.getMangledClassName() + " * ";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("static __global " + owner + " *" + method.getName() +
                    "(__global " + owner + " *this, " + firstType +
                    " one, " + secondType + " two) {\n");
                sb.append("   this->_1 = one;\n");
                sb.append("   this->_2 = two;\n");
                sb.append("   return this;\n");
                sb.append("}");
                return sb.toString();
            }
        };

        List<HardCodedMethodModel> methods = new ArrayList<HardCodedMethodModel>();
        methods.add(new HardCodedMethodModel("_1$mcI$sp", "()" + firstTypeDesc,
              null, true, "_1"));
        methods.add(new HardCodedMethodModel("_2", "()" + secondTypeDesc, null,
              true, "_2"));
        methods.add(new HardCodedMethodModel("_2$mcI$sp", "()" + secondTypeDesc, null,
              true, "_2"));
        methods.add(new HardCodedMethodModel("<init>", "(" + firstTypeDesc +
              secondTypeDesc + ")V", constructorGen, false, null));

        return new Tuple2ClassModel(firstTypeDesc, secondTypeDesc,
            firstTypeClassName, secondTypeClassName, methods, fields);
    }

    @Override
    public boolean matches() {
        return true;
    }

    @Override
    public List<String> getNestedClassNames() {
        List<String> l = new ArrayList<String>(2);
        if (firstTypeDesc.startsWith("L")) {
            l.add(firstTypeClassName);
        }
        if (secondTypeDesc.startsWith("L")) {
            l.add(secondTypeClassName);
        }
        return l;
    }

    @Override
    public String getMangledClassName() {
        return "scala_Tuple2_" + firstTypeClassName.replace(".", "_") + "_" + secondTypeClassName.replace(".", "_");
    }

   @Override
   public boolean classNameMatches(String className) {
      return className.startsWith("scala.Tuple2");
   }

   @Override
   public String toString() {
       return "Tuple2[" + firstTypeDesc + ", " + secondTypeDesc + ", " +
         firstTypeClassName + ", " + secondTypeClassName + "]";
   }
}
