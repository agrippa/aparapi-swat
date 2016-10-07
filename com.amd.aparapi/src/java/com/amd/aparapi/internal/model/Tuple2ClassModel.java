package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;
import com.amd.aparapi.internal.model.HardCodedMethodModel.MethodDefGenerator;
import com.amd.aparapi.internal.writer.KernelWriter;
import com.amd.aparapi.internal.writer.BlockWriter;

public class Tuple2ClassModel extends HardCodedClassModel {
    private static Class<?> clz;
    static {
        try {
            clz = Class.forName("scala.Tuple2");
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    private Tuple2ClassModel(String firstTypeDesc, String secondTypeDesc,
            List<HardCodedMethodModel> methods, List<AllFieldInfo> fields) {
        super(clz, methods, fields, firstTypeDesc, secondTypeDesc);
        initMethodOwners();
    }

    @Override
    public String getDescriptor() {
      return "Lscala/Tuple2<" + descToName(getFirstTypeDesc()).replace('.', '/') + "," +
        descToName(getSecondTypeDesc()).replace('.', '/') + ">";
    }

    @Override
    public boolean merge(HardCodedClassModel other) {
        if (other instanceof Tuple2ClassModel) {
            Tuple2ClassModel otherTuple2 = (Tuple2ClassModel)other;
            if (otherTuple2.getFirstTypeDesc().equals(this.getFirstTypeDesc()) &&
                    otherTuple2.getSecondTypeDesc().equals(this.getSecondTypeDesc())) {
                /*
                 * Realistically this generally just produces a number of
                 * duplicate entries, but if one of the HardCodedClassModels was
                 * marked constructable because it was created from an output
                 * parameter declaration and the other was not then this will
                 * ensure that the resulting merged one has the constructor.
                 */
                if (other != this) {
                    for (HardCodedMethodModel method : other.getMethods()) {
                        getMethods().add(method);
                    }
                }
                return true;
            }
        }
        return false;
    }

    public String getFirstTypeDesc() {
      return paramDescs.get(0);
    }

    public String getSecondTypeDesc() {
      return paramDescs.get(1);
    }

    public static String descToName(String desc) {
        if (desc.startsWith("L")) {
            return desc.substring(1, desc.length() - 1);
        } else {
            return desc;
        }
    }

    public static Tuple2ClassModel create(String firstTypeDesc,
            String secondTypeDesc, boolean isConstructable) {
        final String firstTypeClassName = descToName(firstTypeDesc);
        final String secondTypeClassName = descToName(secondTypeDesc);

        List<AllFieldInfo> fields = new ArrayList<AllFieldInfo>(2);
        fields.add(new AllFieldInfo("_1", firstTypeDesc, firstTypeClassName, -1));
        fields.add(new AllFieldInfo("_2", secondTypeDesc, secondTypeClassName, -1));

        MethodDefGenerator constructorGen = new MethodDefGenerator<Tuple2ClassModel>() {
            private String convertDescToType(final String desc, KernelWriter writer) {
                final String type;
                final String converted = writer.convertType(desc, true);
                if (desc.startsWith("L")) {
                  ClassModel cm = writer.getEntryPoint().getModelFromObjectArrayFieldsClasses(
                      converted.trim(), new ClassModelMatcher() {
                          @Override
                          public boolean matches(ClassModel model) {
                              // No generic types should be allowed for fields of Tuple2s
                              if (model.getClassWeAreModelling().getName().equals(converted.trim())) {
                                  return true;
                              } else {
                                  return false;
                              }
                          }
                      });
                  type = (BlockWriter.emitOcl ? "__global " : "") + cm.getMangledClassName() + " * ";
                } else {
                  type = converted;
                }
                return type;
            }

            @Override
            public String getMethodReturnType(HardCodedMethodModel method,
                    Tuple2ClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();
                return (BlockWriter.emitOcl ? "__global " : "") + owner + "*";
            }

            @Override
            public String getMethodName(HardCodedMethodModel method,
                    Tuple2ClassModel classModel, KernelWriter writer) {
                return method.getName();
            }

            @Override
            public String getMethodArgs(HardCodedMethodModel method,
                    Tuple2ClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();
                final String firstType = convertDescToType(classModel.getFirstTypeDesc(), writer);
                final String secondType = convertDescToType(classModel.getSecondTypeDesc(), writer);
                return (BlockWriter.emitOcl ? "__global " : "") + owner + " *this_ptr, " + firstType + " one, " +
                    secondType + " two";
            }

            @Override
            public String getMethodBody(HardCodedMethodModel method,
                    Tuple2ClassModel classModel, KernelWriter writer) {
                StringBuilder sb = new StringBuilder();
                sb.append("   this_ptr->_1 = one;\n");
                sb.append("   this_ptr->_2 = two;\n");
                sb.append("   return this_ptr;\n");
                return sb.toString();
            }
        };

        List<HardCodedMethodModel> methods = new ArrayList<HardCodedMethodModel>();
        methods.add(new HardCodedMethodModel("_1$mcI$sp", "()I",
              null, true, "_1"));
        methods.add(new HardCodedMethodModel("_2$mcI$sp", "()I", null,
              true, "_2"));

        methods.add(new HardCodedMethodModel("_1$mcD$sp", "()D",
              null, true, "_1"));
        methods.add(new HardCodedMethodModel("_2$mcD$sp", "()D", null,
              true, "_2"));

        methods.add(new HardCodedMethodModel("_1$mcF$sp", "()F",
              null, true, "_1"));
        methods.add(new HardCodedMethodModel("_2$mcF$sp", "()F", null,
              true, "_2"));

        methods.add(new HardCodedMethodModel("_1", "()" + firstTypeDesc, null,
              true, "_1"));
        methods.add(new HardCodedMethodModel("_2", "()" + secondTypeDesc, null,
              true, "_2"));
        if (isConstructable) {
            methods.add(new HardCodedMethodModel("<init>", "(" + firstTypeDesc +
                  secondTypeDesc + ")V", constructorGen, false, null));
        }

        return new Tuple2ClassModel(firstTypeDesc, secondTypeDesc,
            methods, fields);
    }

    @Override
    public List<String> getNestedTypeDescs() {
        List<String> l = new ArrayList<String>(2);
        l.add(getFirstTypeDesc());
        l.add(getSecondTypeDesc());
        return l;
    }

    @Override
    public String getMangledClassName() {
        final String firstTypeClassName = descToName(getFirstTypeDesc());
        final String secondTypeClassName = descToName(getSecondTypeDesc());
        final String fullName = "scala_Tuple2_" + KernelWriter.removeBadChars(firstTypeClassName) + "_" +
          KernelWriter.removeBadChars(secondTypeClassName);
        return fullName.replace('$', '_');
    }

   @Override
   public boolean classNameMatches(String className) {
      return className.startsWith("scala.Tuple2");
   }

   @Override
   public int calcTotalStructSize(Entrypoint entryPoint) {
       int totalSize = 0;
       for (final FieldNameInfo f : getStructMembers()) {
           final String fieldType = f.desc;
           final int pointerSize = Integer.parseInt(entryPoint.getConfig().get(
                       Entrypoint.clDevicePointerSize));
           final int fSize = (fieldType.startsWith("L") ? pointerSize :
                   entryPoint.getSizeOf(fieldType));
           totalSize += fSize;
       }
       return BlockWriter.emitOcl ? totalSize : KernelWriter.roundUpToAlignment(totalSize);
   }

   @Override
   public String toString() {
       return "Tuple2[" + getFirstTypeDesc() + ", " + getSecondTypeDesc() + "]";
   }
}
