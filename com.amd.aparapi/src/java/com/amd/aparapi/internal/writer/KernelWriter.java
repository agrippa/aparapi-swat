/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer. 

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution. 

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission. 

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of 
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration 
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/. 

 */
package com.amd.aparapi.internal.writer;

import com.amd.aparapi.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.RuntimeAnnotationsEntry.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.FullMethodSignature;
import com.amd.aparapi.internal.model.FullMethodSignature.TypeSignature;

import java.util.*;

public abstract class KernelWriter extends BlockWriter{

   public final static int CUDA_STRUCT_ALIGN = 8;
   public static int roundUpToAlignment(int size) {
       return size + (CUDA_STRUCT_ALIGN - (size % CUDA_STRUCT_ALIGN));
   }

   public final static String oclFunctionArgumentsPrefix = "__global void * " +
       "restrict __swat_heap, __global uint * restrict __swat_free_index, " +
       "int * restrict __swat_alloc_failed, const int __swat_heap_size";
   public final static String cudaFunctionArgumentsPrefix = "void * " +
       "__swat_heap, unsigned * __swat_free_index, int * __swat_alloc_failed, " +
       "const int __swat_heap_size";

   public final static String BROADCAST_VALUE_SIG =
       "org/apache/spark/broadcast/Broadcast.value()Ljava/lang/Object;";
   public final static String TUPLE2_CLASSNAME = "scala.Tuple2";
   public final static String DENSEVECTOR_CLASSNAME = "org.apache.spark.mllib.linalg.DenseVector";
   public final static String SPARSEVECTOR_CLASSNAME = "org.apache.spark.mllib.linalg.SparseVector";

   public final static String VECTORS_CLASSNAME = "org/apache/spark/mllib/linalg/Vectors$";
   public final static String DENSE_VECTOR_CREATE_SIG = VECTORS_CLASSNAME +
       ".dense([D)Lorg/apache/spark/mllib/linalg/Vector;";
   public final static String SPARSE_VECTOR_CREATE_SIG = VECTORS_CLASSNAME +
       ".sparse(I[I[D)Lorg/apache/spark/mllib/linalg/Vector;";

   public final static String DENSE_ARRAY_SIG =
       "[Lorg/apache/spark/mllib/linalg/DenseVector;";
   public final static String SPARSE_ARRAY_SIG =
       "[Lorg/apache/spark/mllib/linalg/SparseVector;";

   private final String cvtBooleanToChar = "char ";

   private final String cvtBooleanArrayToCharStar = "char* ";

   private final String cvtByteToChar = "char ";

   private final String cvtByteArrayToCharStar = "char* ";

   private final String cvtCharToShort = "unsigned short ";

   private final String cvtCharArrayToShortStar = "unsigned short* ";

   private final String cvtIntArrayToIntStar = "int* ";

   private final String cvtFloatArrayToFloatStar = "float* ";

   private final String cvtDoubleArrayToDoubleStar = "double* ";

   private final String cvtLongArrayToLongStar = "long* ";

   private final String cvtShortArrayToShortStar = "short* ";

   // private static Logger logger = Logger.getLogger(Config.getLoggerName());

   private Entrypoint entryPoint = null;

   public Entrypoint getEntryPoint() {
     return entryPoint;
   }

   private boolean processingConstructor = false;

   private int countAllocs = 0;

   public static String currentReturnType = null;
   private MethodModel currentMethodModel = null;

   private static String getTypenameFor(String sig) {
     if (sig.startsWith("scala/Tuple2")) {
         int openParenIndex = sig.indexOf('(');
         int closeParenIndex = sig.lastIndexOf(')');

         int commaIndex = openParenIndex + 1;
         int parenNesting = 0;
         while (commaIndex < sig.length()) {
             if (sig.charAt(commaIndex) == '(') {
                 parenNesting++;
             } else if (sig.charAt(commaIndex) == ')') {
                 parenNesting--;
             } else if (parenNesting == 0 && sig.charAt(commaIndex) == ',') {
                 break;
             }
             commaIndex++;
         }

         String templateOne = sig.substring(openParenIndex + 1, commaIndex);
         String templateTwo = sig.substring(commaIndex + 1, closeParenIndex);
         if (templateOne.length() > 1) {
             templateOne = getTypenameFor(templateOne);
             templateOne = templateOne.substring(0, templateOne.length() - 1);
         }
         if (templateTwo.length() > 1) {
             templateTwo = getTypenameFor(templateTwo);
             templateTwo = templateTwo.substring(0, templateTwo.length() - 1);
         }

         return "scala_Tuple2_" + templateOne + "_" + templateTwo + "*";
     } else if (sig.equals("I")) {
         return "int";
     } else if (sig.equals("F")) {
         return "float";
     } else if (sig.equals("D")) {
         return "double";
     } else if (sig.equals("V")) {
         return "void";
     } else if (sig.startsWith("[")) {
         return getTypenameFor(sig.substring(1)) + "*";
     } else {
         // Assume user-defined object. This can be in two different formats:
         // org/apache/spark/rdd/cl/tests/Point(F,F,F) or
         // Lorg/apache/spark/rdd/cl/tests/Point;
         if (sig.startsWith("L")) {
             String typename = sig.substring(1);
             typename = typename.substring(0, typename.length() - 1);
             return typename.replace('/', '_') + "*";
         } else {
             if (sig.indexOf('(') != -1) {
                 sig = sig.substring(0, sig.indexOf('('));
             }
             return sig.replace('/', '_') + "*";
         }
     }
   }

   public static String getTypenameForCurrentReturnType() {
       System.err.println("currentReturnType = \"" + currentReturnType + "\"");
       String converted = getTypenameFor(currentReturnType);
       if (converted.endsWith("*") && BlockWriter.emitOcl) {
           converted = "__global " + converted;
       }
       return converted;
   }

   private Set<MethodModel> mayFailHeapAllocation = null;

   public final boolean multiInput;

   public KernelWriter(boolean multiInput) {
       this.multiInput = multiInput;
   }

   public final static Map<String, String> javaToCLIdentifierMap = new HashMap<String, String>();
   {
      javaToCLIdentifierMap.put("getGlobalId()I", "get_global_id(0)");
      javaToCLIdentifierMap.put("getGlobalId(I)I", "get_global_id"); // no parenthesis if we are conveying args
      javaToCLIdentifierMap.put("getGlobalX()I", "get_global_id(0)");
      javaToCLIdentifierMap.put("getGlobalY()I", "get_global_id(1)");
      javaToCLIdentifierMap.put("getGlobalZ()I", "get_global_id(2)");

      javaToCLIdentifierMap.put("getGlobalSize()I", "get_global_size(0)");
      javaToCLIdentifierMap.put("getGlobalSize(I)I", "get_global_size"); // no parenthesis if we are conveying args
      javaToCLIdentifierMap.put("getGlobalWidth()I", "get_global_size(0)");
      javaToCLIdentifierMap.put("getGlobalHeight()I", "get_global_size(1)");
      javaToCLIdentifierMap.put("getGlobalDepth()I", "get_global_size(2)");

      javaToCLIdentifierMap.put("getLocalId()I", "get_local_id(0)");
      javaToCLIdentifierMap.put("getLocalId(I)I", "get_local_id"); // no parenthesis if we are conveying args
      javaToCLIdentifierMap.put("getLocalX()I", "get_local_id(0)");
      javaToCLIdentifierMap.put("getLocalY()I", "get_local_id(1)");
      javaToCLIdentifierMap.put("getLocalZ()I", "get_local_id(2)");

      javaToCLIdentifierMap.put("getLocalSize()I", "get_local_size(0)");
      javaToCLIdentifierMap.put("getLocalSize(I)I", "get_local_size"); // no parenthesis if we are conveying args
      javaToCLIdentifierMap.put("getLocalWidth()I", "get_local_size(0)");
      javaToCLIdentifierMap.put("getLocalHeight()I", "get_local_size(1)");
      javaToCLIdentifierMap.put("getLocalDepth()I", "get_local_size(2)");

      javaToCLIdentifierMap.put("getNumGroups()I", "get_num_groups(0)");
      javaToCLIdentifierMap.put("getNumGroups(I)I", "get_num_groups"); // no parenthesis if we are conveying args
      javaToCLIdentifierMap.put("getNumGroupsX()I", "get_num_groups(0)");
      javaToCLIdentifierMap.put("getNumGroupsY()I", "get_num_groups(1)");
      javaToCLIdentifierMap.put("getNumGroupsZ()I", "get_num_groups(2)");

      javaToCLIdentifierMap.put("getGroupId()I", "get_group_id(0)");
      javaToCLIdentifierMap.put("getGroupId(I)I", "get_group_id"); // no parenthesis if we are conveying args
      javaToCLIdentifierMap.put("getGroupX()I", "get_group_id(0)");
      javaToCLIdentifierMap.put("getGroupY()I", "get_group_id(1)");
      javaToCLIdentifierMap.put("getGroupZ()I", "get_group_id(2)");

      javaToCLIdentifierMap.put("getPassId()I", "get_pass_id(this_ptr)");

      javaToCLIdentifierMap.put("localBarrier()V", "barrier(CLK_LOCAL_MEM_FENCE)");

      javaToCLIdentifierMap.put("globalBarrier()V", "barrier(CLK_GLOBAL_MEM_FENCE)");
   }

   /**
    * These three convert functions are here to perform
    * any type conversion that may be required between
    * Java and OpenCL.
    * 
    * @param _typeDesc
    *          String in the Java JNI notation, [I, etc
    * @return Suitably converted string, "char*", etc
    */
   @Override public String convertType(String _typeDesc, boolean useClassModel) {
      if (_typeDesc.equals("Z") || _typeDesc.equals("boolean")) {
         return (cvtBooleanToChar);
      } else if (_typeDesc.equals("[Z") || _typeDesc.equals("boolean[]")) {
         return (cvtBooleanArrayToCharStar);
      } else if (_typeDesc.equals("B") || _typeDesc.equals("byte")) {
         return (cvtByteToChar);
      } else if (_typeDesc.equals("[B") || _typeDesc.equals("byte[]")) {
         return (cvtByteArrayToCharStar);
      } else if (_typeDesc.equals("C") || _typeDesc.equals("char")) {
         return (cvtCharToShort);
      } else if (_typeDesc.equals("[C") || _typeDesc.equals("char[]")) {
         return (cvtCharArrayToShortStar);
      } else if (_typeDesc.equals("[I") || _typeDesc.equals("int[]")) {
         return (cvtIntArrayToIntStar);
      } else if (_typeDesc.equals("[F") || _typeDesc.equals("float[]")) {
         return (cvtFloatArrayToFloatStar);
      } else if (_typeDesc.equals("[D") || _typeDesc.equals("double[]")) {
         return (cvtDoubleArrayToDoubleStar);
      } else if (_typeDesc.equals("[J") || _typeDesc.equals("long[]")) {
         return (cvtLongArrayToLongStar);
      } else if (_typeDesc.equals("[S") || _typeDesc.equals("short[]")) {
         return (cvtShortArrayToShortStar);
      }
      // if we get this far, we haven't matched anything yet
      if (useClassModel) {
         return (ClassModel.convert(_typeDesc, "", true));
      } else {
         return _typeDesc;
      }
   }

   @Override public void writeReturn(Return ret) throws CodeGenException {
     if (processingConstructor) {
       write("return (this_ptr)");
     } else if (ret.getStackConsumeCount() > 0) {
       if (ret.getFirstChild() instanceof CompositeArbitraryScopeInstruction) {
           writeSequence(((CompositeArbitraryScopeInstruction)ret.getFirstChild()).getFirstChild(), null, "return ");
       } else {
           write("return(");
           writeInstruction(ret.getFirstChild());
           write(")");
       }
     }
   }

   private String doIndent(String str) {
     StringBuilder builder = new StringBuilder();
     for (int i = 0; i < indent; i++) {
       builder.append("   ");
     }
     builder.append(str);
     return builder.toString();
   }

   @Override public String getAllocCheck() {
     assert(currentReturnType != null);

     final String returnStmt;
     if (currentReturnType.equals("V")) {
         returnStmt = "return;";
     } else {
         final String nullReturn = "(" + getTypenameForCurrentReturnType() + ")0x0";
         returnStmt = "return (" + nullReturn + ");";
     }

     String checkStr = "if (*__swat_alloc_failed) { " + returnStmt + " }";
     String indentedCheckStr = doIndent(checkStr);

     return indentedCheckStr;
   }

   private String generateAllocHelper(String allocVarName, String size, String typeName) {
     final String space = (BlockWriter.emitOcl ? "__global " : "");
     String allocStr = space + typeName + " * " + allocVarName +
       " = (" + space + typeName + " *)alloc(__swat_heap, __swat_free_index, " +
       "__swat_heap_size, " + size + ", __swat_alloc_failed);";
     String indentedAllocStr = doIndent(allocStr);

     String indentedCheckStr = getAllocCheck();

     StringBuilder allocLine = new StringBuilder();
     allocLine.append(indentedAllocStr);
     allocLine.append("\n");
     allocLine.append(indentedCheckStr);
     return allocLine.toString();
   }

   @Override public void writePrimitiveArrayAlloc(I_NEWARRAY newArray) throws CodeGenException {
       final String typeStr;
       switch (newArray.getType()) {
           case (6): // FLOAT
               typeStr = "float";
               break;
           case (7): // DOUBLE
               typeStr = "double";
               break;
           case (10): // INT
               typeStr = "int";
               break;
           case (8): // BYTE
               typeStr = "char";
               break;
           default:
               throw new RuntimeException("Unsupported type=" + newArray.getType());
       }
       String allocVarName = "__alloc" + (countAllocs++);

       markCurrentPosition();
       writeInstruction(newArray.getFirstChild());
       String countStr = eraseToMark();
       String allocStr = generateAllocHelper(allocVarName,
               "sizeof(long) + (sizeof(" + typeStr + ") * (" + countStr + "))",
               typeStr);
       /*
        * TODO This code stores the length of an array as a "header" at the
        * start of the allocation. This allows this allocation to be used to
        * construct a DenseVector by implicitly storing its length. However,
        * this header is not added to input arrays so if the user uses an input
        * DenseVector's values to initialize an output DenseVector, this won't
        * work.
        *
        * Though I guess at that point you might as well just use the input as
        * the output, which would work (I think?).
        */
       final String addressSpace = (BlockWriter.emitOcl ? "__global " : "");
       String storeLengthStr = "*((" + addressSpace + "long *)" + allocVarName + ") = (" +
           countStr + ");";
       String fixAllocVar = allocVarName + " = (" + addressSpace + typeStr +
           " *)(((" + addressSpace + "long *)" + allocVarName + ") + 1); ";

       writeBeforeCurrentLine(allocStr + " " + storeLengthStr + " " + fixAllocVar);
       write(allocVarName);
   }


   @Override public void writeConstructorCall(ConstructorCall call) throws CodeGenException {
     I_INVOKESPECIAL invokeSpecial = call.getInvokeSpecial();

     MethodEntryInfo constructorEntry = invokeSpecial.getConstantPoolMethodEntry();
     final String constructorName = constructorEntry.getMethodName();
     final String constructorSignature = constructorEntry.getMethodSig();

     String guess = inferConstructorType(call);

     MethodModel m = entryPoint.getCallTarget(constructorEntry, true, guess);
     if (m == null) {
         throw new RuntimeException("Unable to find constructor for name=" +
            constructorEntry + " sig=" + constructorSignature);
     }

     write(m.getName());
     write("(");
     if (entryPoint.requiresHeap()) {
         write("__swat_heap, __swat_free_index, __swat_alloc_failed, __swat_heap_size, ");
     }

     String allocVarName = "__alloc" + (countAllocs++);
     String typeName = m.getOwnerClassMangledName();
     String allocStr = generateAllocHelper(allocVarName, "sizeof(" + typeName + ")", typeName);
     writeBeforeCurrentLine(allocStr);

     write(allocVarName);

     for (int i = 0; i < constructorEntry.getStackConsumeCount(); i++) {
       write(", ");
       writeInstruction(invokeSpecial.getArg(i));
     }

     write(")");
   }

   private static String getStaticFieldName(I_GETSTATIC get) {
       return get.getConstantPoolFieldEntry().getNameAndTypeEntry()
           .getNameUTF8Entry().getUTF8();
   }

   private static String getStaticFieldType(I_GETSTATIC get) {
       return get.getConstantPoolFieldEntry().getNameAndTypeEntry()
           .getDescriptorUTF8Entry().getUTF8();
   }

   private static String getStaticFieldClass(I_GETSTATIC get) {
       return get.getConstantPoolFieldEntry().getClassEntry()
           .getNameUTF8Entry().getUTF8();
   }

   @Override public boolean writeMethod(MethodCall _methodCall, MethodEntryInfo _methodEntry) throws CodeGenException {
      final int argc = _methodEntry.getStackConsumeCount();
      final boolean isBroadcasted = _methodEntry.toString().equals(BROADCAST_VALUE_SIG);
      final boolean isDenseVectorCreate = _methodEntry.toString().equals(DENSE_VECTOR_CREATE_SIG);
      final boolean isSparseVectorCreate = _methodEntry.toString().equals(SPARSE_VECTOR_CREATE_SIG);

      final String methodName = _methodEntry.getMethodName();
      final String methodSignature = _methodEntry.getMethodSig();
      final String methodClass = _methodEntry.getClassName();

      if (methodName.equals("<init>")) {
          if (!_methodEntry.toString().equals("java/lang/Object.<init>()V") &&
                !_methodEntry.toString().startsWith("scala/runtime/AbstractFunction1")) {
              writeConstructorCall(new ConstructorCall(
                          ((Instruction)_methodCall).getMethod(),
                          (I_INVOKESPECIAL)_methodCall, null));
          }
          return false;
      }

      if (methodClass.equals("scala/runtime/BoxesRunTime")) {
          final Set<String> ignorableMethods = new HashSet<String>();
          ignorableMethods.add("boxToInteger");
          ignorableMethods.add("boxToFloat");
          ignorableMethods.add("boxToDouble");
          ignorableMethods.add("unboxToFloat");

          if (ignorableMethods.contains(methodName)) {
              writeInstruction(_methodCall.getArg(0));
              return false;
          } else {
              throw new RuntimeException("Encountered unknown boxing method " + methodName);
          }
      }

      final String barrierAndGetterMappings =
          javaToCLIdentifierMap.get(methodName + methodSignature);

      boolean writeAllocCheck = false;
      if (barrierAndGetterMappings != null) {
         // this is one of the OpenCL barrier or size getter methods
         // write the mapping and exit
         if (argc > 0) {
            write(barrierAndGetterMappings);
            write("(");
            for (int arg = 0; arg < argc; arg++) {
               if ((arg != 0)) {
                  write(", ");
               }
               writeInstruction(_methodCall.getArg(arg));
            }
            write(")");
         } else {
            write(barrierAndGetterMappings);
         }
      } else {
         final boolean isSpecial = _methodCall instanceof I_INVOKESPECIAL;
         MethodModel m = entryPoint.getCallTarget(_methodEntry, isSpecial, null);
         writeAllocCheck = mayFailHeapAllocation.contains(m);

         String getterFieldName = null;
         FieldEntry getterField = null;
         if (m != null && m.isGetter()) {
            getterFieldName = m.getGetterField().replace('$', '_');
         }

         if (getterFieldName != null) {
           boolean isObjectField = m.getReturnType().startsWith("L");

           if (isThis(_methodCall.getArg(0))) {
             String fieldName = getterFieldName;
             // if (isObjectField) write("&(");
             write("this_ptr->");
             write(fieldName);
             // if (isObjectField) write(")");
             return false;
           } else if (_methodCall instanceof VirtualMethodCall) {
             VirtualMethodCall virt = (VirtualMethodCall) _methodCall;
             Instruction target = virt.getInstanceReference();
             if (target instanceof I_CHECKCAST) {
               target = target.getPrevPC();
             }

             if (target instanceof AccessLocalVariable) {
               AccessLocalVariable ld = (AccessLocalVariable)target;
               LocalVariableInfo info = ld.getLocalVariableInfo();
               if (info != null) {
                 if (!info.isArray()) {
                   final String varName = info.getVariableName();
                   if (varName.equals("this")) {
                       write("this_ptr->" + getterFieldName);
                   } else {
                       write(info.getVariableName() + "->" + getterFieldName);
                   }
                   return false;
                 }
               } else {
                 int index = ld.getLocalVariableTableIndex();
                 final String varname = BlockWriter.getAnonymousVariableName(
                         this.currentMethodModel, index);
                 write(varname + "->" + getterFieldName);
                 return false;
               }
             } else if (target instanceof VirtualMethodCall) {
               VirtualMethodCall nestedCall = (VirtualMethodCall)target;
               writeMethod(nestedCall, nestedCall.getConstantPoolMethodEntry());
               write("->" + getterFieldName);
               return false;
             } else if (target instanceof AccessArrayElement) {
               AccessArrayElement arrayAccess = (AccessArrayElement)target;

               Instruction refAccess = arrayAccess.getArrayRef();
               if (refAccess instanceof I_CHECKCAST) {
                   refAccess = refAccess.getPrevPC();
               }
               if (refAccess instanceof I_INVOKEVIRTUAL) {
                   I_INVOKEVIRTUAL invoke = (I_INVOKEVIRTUAL)refAccess;
                   MethodEntryInfo callee = invoke.getConstantPoolMethodEntry();
                   if (callee.toString().equals(BROADCAST_VALUE_SIG)) {
                       refAccess = refAccess.getPrevPC();
                   }
               }

               if (!(refAccess instanceof AccessField)) {
                   throw new RuntimeException("Expected AccessField but found " + refAccess);
               }
               //assert refAccess instanceof I_GETFIELD : "ref should come from getfield";
               final String fieldName = ((AccessField) refAccess).getConstantPoolFieldEntry().getNameAndTypeEntry()
                     .getNameUTF8Entry().getUTF8().replace('$', '_');
               write(" (this_ptr->" + fieldName);
               write("[");
               writeInstruction(arrayAccess.getArrayIndex());
               write("])." + getterFieldName);

               return false;
             } else {
                 throw new RuntimeException("Unhandled target \"" +
                         target + "\" for getter " +
                         getterFieldName);
             }
           }
         }
         boolean noCL = _methodEntry.getOwnerClassModel().getNoCLMethods()
               .contains(_methodEntry.getMethodName());
         if (noCL) {
            return false;
         }
         final String intrinsicMapping = Kernel.getMappedMethodName(_methodEntry);
         boolean isIntrinsic = false;
         boolean isInternalMap = false;

         if (intrinsicMapping == null) {
            if (entryPoint == null) {
                throw new RuntimeException("entryPoint should not be null");
            }
            boolean isMapped = Kernel.isMappedMethod(_methodEntry);

            Set<String> scalaMapped = new HashSet<String>();
            scalaMapped.add("scala/math/package$.sqrt(D)D");
            scalaMapped.add("scala/math/package$.pow(DD)D");
            scalaMapped.add("scala/math/package$.exp(D)D");
            scalaMapped.add("scala/math/package$.log(D)D");
            scalaMapped.add("scala/math/package$.abs(I)I");

            boolean isScalaMapped = scalaMapped.contains(_methodEntry.toString());

            if (m != null) {
               write(m.getName());
            } else if (_methodEntry.toString().equals("java/lang/Object.<init>()V")) {
              /*
               * Do nothing if we're in a constructor calling the
               * java.lang.Object super constructor
               */
            } else if (isBroadcasted) {
                /*
                 * Emit nothing if we're fetching the value for a Spark
                 * broadcast variable
                 */
                if (!(_methodCall instanceof VirtualMethodCall)) {
                    throw new RuntimeException("Expected virtual method call");
                }

                Instruction target = ((VirtualMethodCall)_methodCall).getInstanceReference();
                if (!(target instanceof I_GETFIELD)) {
                    throw new RuntimeException("Expected GETFIELD");
                }
                String fieldName = ((I_GETFIELD)target)
                    .getConstantPoolFieldEntry().getNameAndTypeEntry()
                    .getNameUTF8Entry().getUTF8().replace('$', '_');
                write("this_ptr->" + fieldName);
            } else if (isDenseVectorCreate) {
                String allocVarName = "__alloc" + (countAllocs++);
                String allocStr = generateAllocHelper(allocVarName,
                        "sizeof(org_apache_spark_mllib_linalg_DenseVector)",
                        "org_apache_spark_mllib_linalg_DenseVector");
                writeBeforeCurrentLine(allocStr);
                if (BlockWriter.emitOcl) {
                    write("({ " + allocVarName + "->values = ");
                    writeInstruction(_methodCall.getArg(0));
                    write("; " + allocVarName + "->size = *(((" + (BlockWriter.emitOcl ? "__global " : "") + "long *)" +
                            allocVarName + "->values) - 1); ");
                    write("; " + allocVarName + "->tiling = 1; ");
                    write(allocVarName);
                    write("; })");
                } else {
                    write("dense_vec_fill(" + allocVarName + ", ");
                    writeInstruction(_methodCall.getArg(0));
                    write(")");
                }
            } else if (isSparseVectorCreate) {
                String allocVarName = "__alloc" + (countAllocs++);
                String allocStr = generateAllocHelper(allocVarName,
                        "sizeof(org_apache_spark_mllib_linalg_SparseVector)",
                        "org_apache_spark_mllib_linalg_SparseVector");
                writeBeforeCurrentLine(allocStr);
                if (BlockWriter.emitOcl) {
                    write("({ " + allocVarName + "->size = ");
                    writeInstruction(_methodCall.getArg(0));
                    write("; ");
                    write("; " + allocVarName + "->tiling = 1; ");
                    write(allocVarName + "->indices = ");
                    writeInstruction(_methodCall.getArg(1));
                    write("; ");
                    write(allocVarName + "->values = ");
                    writeInstruction(_methodCall.getArg(2));
                    write("; ");
                    write(allocVarName);
                    write("; })");
                } else {
                    write("sparse_vec_fill(" + allocVarName + ", ");
                    writeInstruction(_methodCall.getArg(2));
                    write(", ");
                    writeInstruction(_methodCall.getArg(1));
                    write(", ");
                    writeInstruction(_methodCall.getArg(0));
                    write(", 1)");
                }
            } else {
               // Must be a library call like rsqrt
               if (!isMapped && !isScalaMapped) {
                 throw new RuntimeException(_methodEntry + " should be mapped method!");
               }
               write(methodName);
               isIntrinsic = true;
            }
         } else {
            write(intrinsicMapping);
         }

         if (!isInternalMap && !isBroadcasted && !isDenseVectorCreate && !isSparseVectorCreate) {
             boolean isScalaStaticObjectCall = false;
             write("(");

             boolean haveFirstArg = false;
             if (!isIntrinsic && entryPoint.requiresHeap()) {
                 write("__swat_heap, __swat_free_index, __swat_alloc_failed, __swat_heap_size");
                 haveFirstArg = true;
             }

             if ((intrinsicMapping == null) && !isBroadcasted &&
                     (_methodCall instanceof VirtualMethodCall) && (!isIntrinsic)) {

                 Instruction i = ((VirtualMethodCall) _methodCall).getInstanceReference();
                 if (i instanceof CloneInstruction) {
                     i = ((CloneInstruction)i).getReal();
                 } else if (i instanceof I_CHECKCAST) {
                     i = i.getFirstChild();
                 }

                 if (i instanceof I_ALOAD_0) {
                     if (haveFirstArg) write(", ");
                     write("this_ptr");
                     haveFirstArg = true;
                 } else if (i instanceof AccessLocalVariable || i instanceof I_INVOKEVIRTUAL) {
                     if (haveFirstArg) write(", ");
                     writeInstruction(i);
                     haveFirstArg = true;
                 } else if (i instanceof AccessArrayElement) {
                     final AccessArrayElement arrayAccess = (AccessArrayElement)i;
                     final Instruction refAccess = arrayAccess.getArrayRef();
                     if (refAccess instanceof AccessField) {
                         final String fieldName = ((AccessField) refAccess)
                             .getConstantPoolFieldEntry().getNameAndTypeEntry()
                             .getNameUTF8Entry().getUTF8().replace('$', '_');
                         if (haveFirstArg) write(", ");
                         write("&(this_ptr->" + fieldName);
                         write("[");
                         writeInstruction(arrayAccess.getArrayIndex());
                         write("])");
                         haveFirstArg = true;
                     } else if (refAccess instanceof I_CHECKCAST) {
                         /*
                          * 13: getfield      #305                // Field broadcast$1:Lorg/apache/spark/broadcast/Broadcast;
                          * 16: invokevirtual #311                // Method org/apache/spark/broadcast/Broadcast.value:()Ljava/lang/Object;
                          * 19: checkcast     #313                // class "[Lorg/apache/spark/mllib/linalg/DenseVector;"
                          */
                         I_CHECKCAST cast = (I_CHECKCAST)refAccess;
                         String typeName = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
                         if (!typeName.equals(DENSE_ARRAY_SIG) &&
                                 !typeName.equals(SPARSE_ARRAY_SIG)) {
                             throw new RuntimeException("Unexpected type name " + typeName);
                         }
                         Instruction prev = cast.getPrevPC();
                         if (!(prev instanceof I_INVOKEVIRTUAL)) {
                             throw new RuntimeException("Unexpected prev = " + prev);
                         }
                         I_INVOKEVIRTUAL valueInvoke = (I_INVOKEVIRTUAL)prev;
                         MethodEntryInfo callee = valueInvoke.getConstantPoolMethodEntry();
                         if (!callee.toString().equals(BROADCAST_VALUE_SIG)) {
                             throw new RuntimeException("Expected " +
                                     "Broadcast.value, got " +
                                     callee.toString());
                         }
                         final String fieldName = ((AccessField)valueInvoke.getPrevPC())
                             .getConstantPoolFieldEntry().getNameAndTypeEntry()
                             .getNameUTF8Entry().getUTF8().replace('$', '_');
                         if (haveFirstArg) write(", ");
                         write("&(this_ptr->" + fieldName);
                         write("[");
                         writeInstruction(arrayAccess.getArrayIndex());
                         write("])");
                         haveFirstArg = true;
                     } else {
                         throw new RuntimeException("Unexpected instruction " + refAccess);
                     }

                 } else if (i instanceof New) {
                     // Constructor call
                     assert methodName.equals("<init>");
                     if (haveFirstArg) write(", ");
                     writeInstruction(i);
                     haveFirstArg = true;
                 } else if (i instanceof I_GETSTATIC &&
                         getStaticFieldName((I_GETSTATIC)i).equals("MODULE$")) {
                     /*
                      * Operating on a scala object method with a static method
                      * call, do nothing as there is not "this" to pass.
                      */
                     isScalaStaticObjectCall = true;
                 } else {
                     throw new RuntimeException("unhandled call to " +
                             _methodEntry + " from: " + i);
                 }
             }

             for (int arg = 0; arg < argc; arg++) {
                 if (haveFirstArg) {
                     write(", ");
                 }
                 Instruction argInsn = _methodCall.getArg(arg);
                 writeInstruction(_methodCall.getArg(arg));
                 if (argInsn instanceof AccessLocalVariable) {
                     AccessLocalVariable localAccess = (AccessLocalVariable)argInsn;
                     LocalVariableInfo info = localAccess.getLocalVariableInfo();
                     if (info != null && info.isArray()) {
                         write(", " + info.getVariableName() + BlockWriter.arrayLengthMangleSuffix);
                     }
                 } else if (argInsn instanceof AccessInstanceField) {
                     AccessInstanceField fieldAccess = (AccessInstanceField)argInsn;
                     FieldEntry entry = fieldAccess.getConstantPoolFieldEntry();

                     String fieldName = entry.getNameAndTypeEntry()
                         .getNameUTF8Entry().getUTF8().replace('$', '_');
                     String fieldDesc = entry.getNameAndTypeEntry()
                         .getDescriptorUTF8Entry().getUTF8();
                     if (fieldDesc.startsWith("[")) {
                        write(", this_ptr->" + fieldName + BlockWriter.arrayLengthMangleSuffix);
                     }
                 }

                 haveFirstArg = true;
             }
             write(")");
         }
      }
      return writeAllocCheck;
   }

   private boolean isThis(Instruction instruction) {
      return instruction instanceof I_ALOAD_0;
   }

   public void writePragma(String _name, boolean _enable) {
      if (BlockWriter.emitOcl) {
          write("#pragma OPENCL EXTENSION " + _name + " : " + (_enable ? "en" : "dis") + "able");
          newLine();
      }
   }

   public final static String __local = "__local";

   public final static String __global = "__global";

   public final static String __constant = "__constant";

   public final static String __private = "__private";

   public final static String LOCAL_ANNOTATION_NAME = "L" + com.amd.aparapi.Kernel.Local.class.getName().replace('.', '/') + ";";

   public final static String CONSTANT_ANNOTATION_NAME = "L" + com.amd.aparapi.Kernel.Constant.class.getName().replace('.', '/')
         + ";";

   private boolean doesHeapAllocation(MethodModel mm,
       Set<MethodModel> mayFailHeapAllocation) {
     if (mayFailHeapAllocation.contains(mm)) {
       return true;
     }

     for (MethodModel callee : mm.getCalledMethods()) {
       if (doesHeapAllocation(callee, mayFailHeapAllocation)) {
         mayFailHeapAllocation.add(mm);
         return true;
       }
     }

     return false;
   }

   public static String removeBadChars(String desc) {
       final String tmp;
       if (desc.indexOf("<") != -1) {
           final String[] typeParams = Entrypoint.parseTypeParameters(desc);
           final StringBuilder sb = new StringBuilder();
           sb.append(desc.substring(0, desc.indexOf("<")));
           for (String t : typeParams) {
               sb.append("_");
               if (t.startsWith("L")) {
                   if (!t.endsWith(";")) throw new RuntimeException();
                   t = t.substring(1, t.length() - 1);
               }
               sb.append(t);
           }
           tmp = sb.toString();
       } else {
           tmp = desc;
       }
       return tmp.replace('.', '_').replace(';', '_');
   }

   private void emitExternalObjectDef(ClassModel cm) {
       final ArrayList<FieldNameInfo> fieldSet = cm.getStructMembers();

       final String mangledClassName = cm.getMangledClassName();
       newLine();
       if (BlockWriter.emitOcl) {
           write("struct __attribute__ ((packed)) " + mangledClassName + "_s{");
       } else {
           write("struct __align__(" + CUDA_STRUCT_ALIGN + ") " + mangledClassName + "_s{");
       }
       in();
       newLine();

       if (fieldSet.size() > 0) {
           int totalSize = 0;
           int alignTo = 0;

           final Iterator<FieldNameInfo> it = fieldSet.iterator();
           while (it.hasNext()) {
               final FieldNameInfo field = it.next();
               final String fType = field.desc;
               final int fSize = entryPoint.getSizeOf(fType);

               if (fSize > alignTo) {
                   alignTo = fSize;
               }
               totalSize += fSize;

               String cType = convertType(field.desc, true);
               if (field.desc.startsWith("L")) {
                   cType = (BlockWriter.emitOcl ? "__global " : "") + removeBadChars(cType) + " *";
               } else if (field.desc.startsWith("[")) {
                   cType = (BlockWriter.emitOcl ? "__global " : "") + cType;
               }
               assert cType != null : "could not find type for " + field.desc;
               writeln(cType + " " + field.name + ";");
           }

           // compute total size for OpenCL buffer
           int totalStructSize = 0;
           if ((totalSize % alignTo) == 0) {
               totalStructSize = totalSize;
           } else {
               // Pad up if necessary
               totalStructSize = ((totalSize / alignTo) + 1) * alignTo;
           }

       }
       out();
       newLine();
       // write("} " + mangledClassName + ";");
       write("};");
       newLine();
       newLine();
   }

   class SignatureMatcher extends ClassModelMatcher {
       private final TypeSignature targetSig;

       public SignatureMatcher(TypeSignature targetSig) {
           this.targetSig = targetSig;
       }

       @Override
       public boolean matches(ClassModel model) {
           String modelDesc = "L" + model.getClassWeAreModelling().getName().replace('.', '/') + ";";
           if (modelDesc.equals(targetSig.getBaseType())) {
               if (model instanceof HardCodedClassModel) {
                   HardCodedClassModel hc = (HardCodedClassModel)model;

                   TypeParameters hcTypes = hc.getTypeParamDescs();
                   List<String> targetTypes = targetSig.getTypeParameters();

                   if (hcTypes.size() == targetTypes.size()) {
                       for (int index = 0; index < hcTypes.size(); index++) {
                           String target = targetTypes.get(index);
                           String curr = hcTypes.get(index);
                           if (!TypeSignature.compatible(target, curr)) {
                               return false;
                           }
                       }
                       return true;
                   } else {
                       return false;
                   }
               } else {
                   if (!targetSig.getTypeParameters().isEmpty()) {
                       throw new RuntimeException("Do not support mathing " +
                           "loaded classes with generic types");
                   }
                   return true;
               }
           } else {
               return false;
           }

       }
   }

   private static boolean isMemberOfScalaObject(MethodModel mm) {
       ClassModel classModel = mm.getMethod().getClassModel();
       Class<?> clazz = classModel.getClassWeAreModelling();
       try {
           clazz.getDeclaredField("MODULE$");
       } catch (NoSuchFieldException n) {
           return false;
       }
       return true;
   }

   private void writeNormalizeToHeap(String varname, String castTo) {
       final String addressSpace = (BlockWriter.emitOcl ? "__global " : "");
       writeln(varname + " = " +
               "(" + addressSpace + castTo + " *)(((" + addressSpace + "char *)" + varname + ") - " +
               "((" + addressSpace + "char *)heap));");
   }

   // member == 1 or member == 2
   private void writeTuple2MemberUpdate(String varname, int member,
           ScalaArrayParameter outParam, boolean topLevel) throws ClassNotFoundException {
       final String fieldName = varname + "->_" + (member + 1);

       if (outParam.typeParameterIsObject(member)) {

           if (outParam.getTypeParameter(member).equals("L" +
                       DENSEVECTOR_CLASSNAME + ";")) {
               writeDenseVectorValueUpdate(fieldName);
           } else if (outParam.getTypeParameter(member).equals("L" +
                       SPARSEVECTOR_CLASSNAME + ";")) {
               writeSparseVectorValueUpdate(fieldName);
           } else if (outParam.getTypeParameter(member).startsWith("L" +
                       TUPLE2_CLASSNAME)) {
               ScalaTuple2ArrayParameter actual = (ScalaTuple2ArrayParameter)outParam;

               String nestedParam = actual.getTypeParameter(member);
               if (nestedParam.startsWith("L")) {
                   if (!nestedParam.endsWith(";")) throw new RuntimeException();
                   nestedParam = nestedParam.substring(1, nestedParam.length() - 1);
               }
             
               final ScalaArrayParameter nestedOutParam;
               if (nestedParam.indexOf("<") != -1) {
                   String[] nestedNestedParams = nestedParam.substring(
                           nestedParam.indexOf("<") + 1,
                           nestedParam.indexOf(">")).split(",");
                   String baseType = nestedParam.substring(0, nestedParam.indexOf("<"));
                   nestedOutParam =
                       ScalaArrayParameter.createArrayParameterFor(
                               baseType, Class.forName(baseType),
                               fieldName, ScalaParameter.DIRECTION.OUT);
                   for (int i = 0; i < nestedNestedParams.length; i++) {
                       nestedOutParam.addTypeParameter(nestedNestedParams[i],
                               nestedNestedParams[i].startsWith("L"));
                   }
               } else {
                   nestedOutParam =
                       ScalaArrayParameter.createArrayParameterFor(
                               nestedParam, Class.forName(nestedParam), fieldName,
                               ScalaParameter.DIRECTION.OUT);
               }
               writeOutputUpdate(fieldName, nestedOutParam, false);
               newLine();
           }

           if (!topLevel) {
               writeNormalizeToHeap(fieldName, "void");
           }

           if (topLevel) {
               write(outParam.getName() + "_" + (member + 1) + "[i] = *(" +
                   varname + "->_" + (member + 1) + ");");
           }

       } else {
           if (topLevel) {
               write(outParam.getName() + "_" + (member + 1) + "[i] = " + varname +
                   "->_" + (member + 1) + ";");
           }
       }
   }

   private void writeDenseVectorValueUpdate(String varname) {
       writeNormalizeToHeap(varname + "->values", "double");
       writeln(varname + "->tiling = iter;");
   }

   private void writeSparseVectorValueUpdate(String varname) {
       writeNormalizeToHeap(varname + "->values", "double");
       writeNormalizeToHeap(varname + "->indices", "int");
       writeln(varname + "->tiling = iter;");
   }

   private void writeOutputUpdate(String varname, ScalaArrayParameter outParam, boolean topLevel) throws ClassNotFoundException {
       if (outParam.getClazz() != null) {
           if (outParam.getClazz().getName().equals(TUPLE2_CLASSNAME)) {
               writeTuple2MemberUpdate(varname, 0, outParam, topLevel);
               newLine();
               writeTuple2MemberUpdate(varname, 1, outParam, topLevel);
           } else if (outParam.getClazz().getName().equals(
                       DENSEVECTOR_CLASSNAME)) {
               // Offset in bytes
               writeDenseVectorValueUpdate(varname);
               write(outParam.getName() + "[i] = *" + varname + ";");
           } else if (outParam.getClazz().getName().equals(
                       SPARSEVECTOR_CLASSNAME)) {
               // Offset in bytes
               writeSparseVectorValueUpdate(varname);
               write(outParam.getName() + "[i] = *" + varname + ";");
           } else {
               write(outParam.getName() + "[i] = *" + varname + ";");
           }
       } else if (outParam instanceof ScalaArrayOfArraysParameter) {
           writeln(outParam.getName() + "_iters[i] = iter;");
           writeln(outParam.getName() + "[i] = " +
                   "((" + (BlockWriter.emitOcl ? "__global" : "") + " char *)result) - " +
                   "((" + (BlockWriter.emitOcl ? "__global" : "") + " char *)heap);");
       }
   }

   @Override public void write(Entrypoint _entryPoint,
         Collection<ScalaArrayParameter> params) throws CodeGenException, ClassNotFoundException {
      final List<String> thisStruct = new ArrayList<String>();
      final List<String> argLines = new ArrayList<String>();
      final List<String> assigns = new ArrayList<String>();

      entryPoint = _entryPoint;

      ScalaArrayParameter outParam = null;
      for (ScalaArrayParameter p : params) {
          if (p.getDir() == ScalaArrayParameter.DIRECTION.OUT) {
              if (outParam != null) {
                  throw new RuntimeException("Multiple output parameters?");
              }
              outParam = p;
          }
      }
      if (outParam == null) throw new RuntimeException("No out param");

      for (final ClassModelField field : _entryPoint.getReferencedClassModelFields()) {
         final StringBuilder thisStructLine = new StringBuilder();
         final StringBuilder argLine = new StringBuilder();
         final StringBuilder assignLine = new StringBuilder();

         String signature = field.getDescriptor();

         ScalaParameter param = null;

         if (multiInput) {
            if (signature.equals("[D") || signature.equals("[F") ||
                    signature.equals("[I") || signature.equals("[B")) {
                final String primitiveType;
                if (signature.equals("[D")) {
                    primitiveType = "double";
                } else if (signature.equals("[F")) {
                    primitiveType = "float";
                } else if (signature.equals("[I")) {
                    primitiveType = "int";
                } else if (signature.equals("[B")) {
                    primitiveType = "char";
                } else {
                    throw new RuntimeException("Unsupported: \"" + signature +
                            "\"");
                }

                param = new ScalaArrayOfArraysParameter(signature,
                        field.getName(), ScalaParameter.DIRECTION.IN,
                        primitiveType);
            } else if (signature.equals("I") || signature.equals("F") ||
                    signature.equals("D") || signature.equals("[B")) {
                param = new ScalaPrimitiveOrObjectArrayParameter("[" + signature,
                        field.getName(), ScalaParameter.DIRECTION.IN);
            } else {
               throw new RuntimeException("Unsupported: \"" + signature + "\"");
            }
         } else {
            if (signature.equals("[Lorg/apache/spark/mllib/linalg/DenseVector")) {
                param = new ScalaDenseVectorArrayParameter(signature,
                        field.getName(), ScalaParameter.DIRECTION.IN);
            } else if (signature.equals("[Lorg/apache/spark/mllib/linalg/SparseVector")) {
                param = new ScalaSparseVectorArrayParameter(signature,
                        field.getName(), ScalaParameter.DIRECTION.IN);
            } else if (signature.startsWith("[Lscala/Tuple2")) {
                param = new ScalaTuple2ArrayParameter(signature, field.getName(),
                        ScalaParameter.DIRECTION.IN);
            } else if (signature.startsWith("[")) {
                param = new ScalaPrimitiveOrObjectArrayParameter(signature,
                        field.getName(), ScalaParameter.DIRECTION.IN);
            } else {
                param = new ScalaScalarParameter(signature, field.getName());
            }
         }

         // check the suffix

         boolean isPointer = signature.startsWith("[");

         argLine.append(param.getInputParameterString(this));
         thisStructLine.append(param.getStructString(this));
         assignLine.append(param.getGlobalInitString(this));

         assigns.add(assignLine.toString());
         argLines.add(argLine.toString());
         thisStruct.add(thisStructLine.toString());

         // Add int field into "this" struct for supporting java arraylength op
         // named like foo__javaArrayLength
         if (isPointer && true /* _entryPoint.getArrayFieldArrayLengthUsed().contains(field.getName()) */ ) {
            final StringBuilder lenStructLine = new StringBuilder();
            final StringBuilder lenArgLine = new StringBuilder();
            final StringBuilder lenAssignLine = new StringBuilder();

            String suffix = "";
            String lenName = field.getName().replace('$', '_') + BlockWriter.arrayLengthMangleSuffix + suffix;

            lenStructLine.append("int " + lenName);
            thisStruct.add(lenStructLine.toString());

            if (!multiInput) {
               lenAssignLine.append("this_ptr->");
               lenAssignLine.append(lenName.replace('$', '_'));
               lenAssignLine.append(" = ");
               lenAssignLine.append(lenName.replace('$', '_'));
               assigns.add(lenAssignLine.toString());

               lenArgLine.append("int " + lenName);
               argLines.add(lenArgLine.toString());
            }
         }
      }

      if (_entryPoint.requiresHeap()) {
        // assigns.add("this->heap = heap");
        // assigns.add("this->free_index = free_index");
        // assigns.add("this->heap_size = heap_size");

        // thisStruct.add("__global void *heap");
        // thisStruct.add("__global uint *free_index");
        // thisStruct.add("int alloc_failed");
        // thisStruct.add("unsigned int heap_size");
      }

      if (Config.enableByteWrites || _entryPoint.requiresByteAddressableStorePragma()) {
         // Starting with OpenCL 1.1 (which is as far back as we support)
         // this feature is part of the core, so we no longer need this pragma
         if (false) {
            writePragma("cl_khr_byte_addressable_store", true);
            newLine();
         }
      }

      boolean usesAtomics = false;
      if (Config.enableAtomic32 || _entryPoint.requiresAtomic32Pragma() || _entryPoint.requiresHeap()) {
         usesAtomics = true;
         writePragma("cl_khr_global_int32_base_atomics", true);
         writePragma("cl_khr_global_int32_extended_atomics", true);
         writePragma("cl_khr_local_int32_base_atomics", true);
         writePragma("cl_khr_local_int32_extended_atomics", true);
      }

      if (Config.enableAtomic64 || _entryPoint.requiresAtomic64Pragma()) {
         usesAtomics = true;
         writePragma("cl_khr_int64_base_atomics", true);
         writePragma("cl_khr_int64_extended_atomics", true);
      }

      if (usesAtomics && BlockWriter.emitOcl) {
         write("static int atomicAdd(__global int *_arr, int _index, int _delta){");
         in();
         {
            newLine();
            write("return atomic_add(&_arr[_index], _delta);");
            out();
            newLine();
         }
         write("}");

         newLine();
      }

      // if (Config.enableDoubles || _entryPoint.requiresDoublePragma()) {
         writePragma("cl_khr_fp64", true);
         newLine();
      // }

      // Heap allocation
      if (BlockWriter.emitOcl) {
          write("static __global void *alloc(__global void *heap, " +
                  "volatile __global uint *free_index, unsigned int heap_size, " +
                  "int nbytes, int *alloc_failed) {");
          in();
          newLine();
          {
            writeln("__global unsigned char *cheap = (__global unsigned char *)heap;");
            writeln("uint rounded = nbytes + (256 - (nbytes % 256));");
            writeln("uint offset = atomic_add(free_index, rounded);");
            writeln("if (offset + nbytes > heap_size) { *alloc_failed = 1; return 0x0; }");
            write("else return (__global void *)(cheap + offset);");
          }
          out();
          newLine();
          write("}");
          newLine();
      } else {
          write("static __device__ void *alloc(void *heap, " +
                  "volatile unsigned *free_index, unsigned int heap_size, " +
                  "int nbytes, int *alloc_failed) {");
          in();
          newLine();
          {
            writeln("unsigned char *cheap = (unsigned char *)heap;");
            writeln("unsigned rounded = nbytes + (256 - (nbytes % 256));");
            writeln("unsigned offset = atomicAdd((unsigned int *)free_index, rounded);");
            writeln("if (offset + nbytes > heap_size) { *alloc_failed = 1; return 0x0; }");
            write("else return (void *)(cheap + offset);");
          }
          out();
          newLine();
          write("}");
          newLine();
          writeln("template<typename T>");
          writeln("inline T *dense_vec_fill(T *alloc, double *vals) {");
          writeln("    alloc->values = vals;");
          writeln("    alloc->size = *(((long *)vals) - 1);");
          writeln("    alloc->tiling = 1;");
          writeln("    return alloc;");
          writeln("}");
          writeln("template<typename T>");
          writeln("inline T *sparse_vec_fill(T *alloc, double *vals, int *indices, unsigned size, unsigned tiling) {");
          writeln("    alloc->values = vals;");
          writeln("    alloc->indices = indices;");
          writeln("    alloc->size = size;");
          writeln("    alloc->tiling = tiling;");
          writeln("    return alloc;");
          writeln("}");
      }

      // Emit structs for oop transformation accessors
      List<String> lexicalOrdering = _entryPoint.getLexicalOrderingOfObjectClasses();
      Set<String> emitted = new HashSet<String>();
      for (String className : lexicalOrdering) {
        for (final ClassModel cm : _entryPoint.getModelsForClassName(className)) {
            final String mangled = cm.getMangledClassName();
            if (emitted.contains(mangled)) continue;

            if (BlockWriter.emitOcl) {
                writeln("typedef struct __attribute__ ((packed)) " + mangled + "_s " + mangled + ";");
            } else {
                writeln("typedef struct __align__(" + CUDA_STRUCT_ALIGN + ") " + mangled + "_s " + mangled + ";");
            }
            emitted.add(mangled);
        }
      }

      emitted.clear();

      for (String className : lexicalOrdering) {
        for (final ClassModel cm : _entryPoint.getModelsForClassName(className)) {
            final String mangled = cm.getMangledClassName();
            if (emitted.contains(mangled)) continue;

            emitExternalObjectDef(cm);
            emitted.add(mangled);
        }
      }

      write("typedef struct This_s{");

      in();
      newLine();
      for (final String line : thisStruct) {
         write(line);
         writeln(";");
      }
      out();
      write("} This;");
      newLine();

      final List<MethodModel> merged = new ArrayList<MethodModel>(
              _entryPoint.getCalledMethods().size() + 1);
      merged.addAll(_entryPoint.getCalledMethods());
      merged.add(_entryPoint.getMethodModel());

      assert(mayFailHeapAllocation == null);
      mayFailHeapAllocation = new HashSet<MethodModel>();
      for (final MethodModel mm : merged) {
        if (mm.requiresHeap()) mayFailHeapAllocation.add(mm);
      }

      for (final MethodModel mm : merged) {
        doesHeapAllocation(mm, mayFailHeapAllocation);
      }

      final Set<String> methodsWritten = new HashSet<String>();
      for (HardCodedClassModel model : _entryPoint.getHardCodedClassModels()) {
          for (HardCodedMethodModel method : model.getMethods()) {
              if (!methodsWritten.contains(method.getName()) &&
                          !method.isGetter()) {
                  methodsWritten.add(method.getName());
                  newLine();
                  write(method.getMethodDef(model, this));
                  newLine();
                  newLine();
              }
          }
      }

      for (final MethodModel mm : merged) {
         // write declaration :)
         if (mm.isPrivateMemoryGetter()) {
            continue;
         }
         boolean isParallelModel = entryPoint.isParallelClassModel(
                 mm.getMethod().getClassModel());
         String addressSpace = isParallelModel ? "__local" : "__global";

         final String returnType = mm.getReturnType();

         if (returnType.equals("V")) {
             this.currentReturnType = "void";
         } else {
             Instruction last = getLastInstructionInSequence(mm.getExprHead(), null);
             if (!(last instanceof Return)) {
                 throw new RuntimeException("Unexpected last method instruction " + last.getClass().getName());
             }
             Return ret = (Return)last;
             Instruction value = ret.getFirstChild();
             this.currentReturnType = inferType(value);
         }

         // this.currentReturnType = returnType;
         this.currentMethodModel = mm;

         final String fullReturnType;
         final String convertedReturnType = convertType(returnType, true);
         if (mm == _entryPoint.getMethodModel()) {
             // We already know the return type for the entrypoint
             fullReturnType = outParam.getType().replace("[]", "*");
         } else if (returnType.startsWith("L")) {
           SignatureEntry sigEntry =
               mm.getMethod().getAttributePool().getSignatureEntry();
           final TypeSignature sig;
           if (sigEntry != null) {
               sig = new FullMethodSignature(sigEntry.getSignature()).getReturnType();
           } else {
               sig = new TypeSignature(returnType);
           }
           ClassModel cm = entryPoint.getModelFromObjectArrayFieldsClasses(
               convertedReturnType.trim(), new SignatureMatcher(sig));
           fullReturnType = cm.getMangledClassName();
         } else {
           fullReturnType = convertedReturnType;
         }

         if (mm.getSimpleName().equals("<init>")) {
           // Transform constructors to return a reference to their object type
           ClassModel owner = mm.getMethod().getClassModel();
           write("static " + (BlockWriter.emitOcl ? addressSpace : "") + " " +
                   owner.getClassWeAreModelling().getName().replace('.', '_') +
                   " * ");
           processingConstructor = true;
         } else if (returnType.startsWith("L")) {
           write("static " + (BlockWriter.emitOcl ? "__global " : "") + fullReturnType);
           write(" *");
           processingConstructor = false;
         } else {
           // Arrays always map to __private or__global arrays
           if (returnType.startsWith("[")) {
              write("static " + (BlockWriter.emitOcl ? "__global " : ""));
           } else {
             write("static ");
           }
           write(fullReturnType + " ");
           processingConstructor = false;
         }

         write(mm.getName() + "(");

         boolean alreadyHasFirstArg = false;

         if (_entryPoint.requiresHeap()) {
             if (BlockWriter.emitOcl) {
                 write(oclFunctionArgumentsPrefix);
             } else {
                 write(cudaFunctionArgumentsPrefix);
             }
             alreadyHasFirstArg = true;
         }

         // Write this argument
         if (!mm.getMethod().isStatic() && !isMemberOfScalaObject(mm)) {
            if ((mm.getMethod().getClassModel() == _entryPoint.getClassModel())
                  || mm.getMethod().getClassModel().isSuperClass(
                      _entryPoint.getClassModel().getClassWeAreModelling())) {
               if (alreadyHasFirstArg) write(", ");
               write("This *this_ptr");
               alreadyHasFirstArg = true;
            } else {
               // Call to an object member or superclass of member
               Iterator<ClassModel> classIter = _entryPoint.getObjectArrayFieldsClassesIterator();
               while (classIter.hasNext()) {
                  final ClassModel c = classIter.next();
                  if (mm.getMethod().getClassModel() == c) {
                     if (alreadyHasFirstArg) write(", ");
                     String space = (isParallelModel ? (processingConstructor ? "__local" : "") : "__global");
                     if (!BlockWriter.emitOcl) space = "";
                     write(space + " " + mm.getMethod().getClassModel()
                             .getClassWeAreModelling().getName().replace('.',
                                 '_') + " *this_ptr");
                     alreadyHasFirstArg = true;
                     break;
                  } else if (mm.getMethod().getClassModel().isSuperClass(
                              c.getClassWeAreModelling())) {
                     if (alreadyHasFirstArg) write(", ");
                     String space = (isParallelModel ? (processingConstructor ? "__local" : "") : "__global");
                     if (!BlockWriter.emitOcl) space = "";
                     write(space + " " +
                             c.getClassWeAreModelling().getName().replace('.',
                                 '_') + " *this_ptr");
                     alreadyHasFirstArg = true;
                     break;
                  }
               }
            }
         }

         final LocalVariableTableEntry<LocalVariableInfo> lvte = mm.getLocalVariableTableEntry();
         for (final LocalVariableInfo lvi : lvte) {
            if ((lvi.getStart() == 0) && ((lvi.getVariableIndex() != 0) ||
                        mm.getMethod().isStatic())) { // full scope but skip this
               final String descriptor = lvi.getVariableDescriptor();

               if (alreadyHasFirstArg) {
                  write(", ");
               }

               // Arrays always map to __global arrays
               if (BlockWriter.emitOcl && descriptor.startsWith("[")) {
                  write(" __global ");
               }

               if (BlockWriter.emitOcl && descriptor.startsWith("L")) {
                 write("__global ");
               }
               final String convertedType;
               if (descriptor.startsWith("L")) {
                 final String converted = convertType(descriptor, true).trim();
                 final SignatureEntry sigEntry = mm.getMethod().getAttributePool().getSignatureEntry();
                 final TypeSignature sig;

                 if (sigEntry != null) {
                     final int argumentOffset = (mm.getMethod().isStatic() ?
                         lvi.getVariableIndex() : lvi.getVariableIndex() - 1);
                     final FullMethodSignature methodSig = new FullMethodSignature(
                         sigEntry.getSignature());
                     sig =
                         methodSig.getTypeParameters().get(argumentOffset);
                 } else {
                     sig = new TypeSignature(descriptor);
                 }
                 ClassModel cm = entryPoint.getModelFromObjectArrayFieldsClasses(
                     converted, new SignatureMatcher(sig));
                 convertedType = cm.getMangledClassName() + "* ";
               } else {
                 convertedType = convertType(descriptor, true);
               }

               write(convertedType);
               write(lvi.getVariableName());
               alreadyHasFirstArg = true;

               if (descriptor.startsWith("[")) {
                   write(", int " + lvi.getVariableName() +
                           BlockWriter.arrayLengthMangleSuffix);
               }
            }
         }
         write(")");
         writeMethodBody(mm);
         newLine();
      }

      if (BlockWriter.emitOcl) {
          write("__kernel void run(");
      } else {
          write("extern \"C\" __global__ void run(");
      }
      in(); in();
      newLine();
      {
         boolean first = true;
         if (multiInput) {
            for (final String line : argLines) {
              if (!first) write(", ");
              write(line);
              first = false;
            }
         }

         for (ScalaArrayParameter p : params) {
            if (first) {
               first = false;
            } else {
               write(", ");
               newLine();
            }

            if (p.getDir() == ScalaArrayParameter.DIRECTION.OUT) {
               write(p.getOutputParameterString(this));
            } else {
               write(p.getInputParameterString(this));
            }
         }

         if (!multiInput) {
            for (final String line : argLines) {
              if (!first) write(", ");
              write(line);
              first = false;
            }
         }

         if (_entryPoint.requiresHeap()) {
            if (BlockWriter.emitOcl) {
                write(", __global void * restrict heap, __global uint * " +
                        "restrict free_index, unsigned int heap_size, __global " +
                        "int * restrict processing_succeeded");
            } else {
                write(", void * heap, unsigned * free_index, unsigned int " +
                        "heap_size, int * processing_succeeded");
            }
         }
         write(", int N, int iter");
      }
      write(") {");
      out();
      newLine();

      writeln("This thisStruct;");
      writeln("This* this_ptr=&thisStruct;");

      if (!multiInput) {
          for (final String line : assigns) {
             write(line);
             writeln(";");
          }
      }

      for (ScalaArrayParameter p : params) {
        if (p.getDir() == ScalaArrayParameter.DIRECTION.IN) {
          if (p.getClazz() != null &&
                  _entryPoint.getHardCodedClassModels().haveClassModelFor(
                      p.getClazz())) {
            writeln((BlockWriter.emitOcl ? "__global " : "") + p.getType() + " *my_" + p.getName() + " = " +
                p.getName() + " + " +
                (BlockWriter.emitOcl ? "get_global_id(0)" : "(blockIdx.x * blockDim.x + threadIdx.x)") + ";");
          }
        }
      }

    if (BlockWriter.emitOcl) {
        write("for (int i = get_global_id(0); i < N; i += get_global_size(0)) {");
    } else {
        write("for (int i = (blockIdx.x * blockDim.x + threadIdx.x); i < N; i += (gridDim.x * blockDim.x)) {");
    }
      in();
      newLine();
      {
         if (_entryPoint.requiresHeap()) {
           writeln("if (iter == 0) processing_succeeded[i] = 0;");
           writeln("else if (processing_succeeded[i]) continue;");
           writeln("int alloc_failed = 0;");
         }

         if (multiInput) {
             for (final String line : assigns) {
                write(line);
                writeln(";");
             }
         }


         for (ScalaArrayParameter p : params) {
           if (p.getDir() == ScalaParameter.DIRECTION.IN) {
             if (p.getClazz() != null &&
                     (p.getClazz().getName().equals(TUPLE2_CLASSNAME) ||
                      p.getClazz().getName().equals(DENSEVECTOR_CLASSNAME) ||
                      p.getClazz().getName().equals(SPARSEVECTOR_CLASSNAME))) {
                 writeln(p.getInputInitString(this, p.getName()));
             }
           }
         }

         StringBuilder startingArguments = new StringBuilder();
         if (_entryPoint.requiresHeap()) {
             startingArguments.append("heap, free_index, &alloc_failed, " +
                     "heap_size, ");
         }
         startingArguments.append("this_ptr");

         if (outParam.getClazz() != null) {
           write((BlockWriter.emitOcl ? "__global " : "") + outParam.getType() + "* result = " +
               _entryPoint.getMethodModel().getName() + "(" +
               startingArguments.toString());
         } else if (outParam instanceof ScalaArrayOfArraysParameter) {
           final String primitiveType =
               ((ScalaArrayOfArraysParameter)outParam).primitiveElementType;
           write((BlockWriter.emitOcl ? "__global " : "") + primitiveType + "* result = " +
                   _entryPoint.getMethodModel().getName() + "(" +
                   startingArguments.toString());
         } else {
           write(outParam.getName() + "[i] = " +
                   _entryPoint.getMethodModel().getName() + "(" +
                   startingArguments.toString());
         }

         for (ScalaArrayParameter p : params) {
           if (p.getDir() == ScalaParameter.DIRECTION.IN) {
             if (p.getType().endsWith("[]")) {
               write(", " + p.getName() + " + " + p.getName() +
                       "_offsets[i], " + p.getName() + "_sizes[i]");
             } else if (p.getClazz() == null) {
               write(", " + p.getName() + "[i]");
             } else if (p.getClazz().getName().equals("scala.Tuple2")) {
               write(", my_" + p.getName());
             } else {
               write(", " + p.getName() + " + i");
             }
           }
         }
         write(");");
         newLine();

         if (_entryPoint.requiresHeap()) {
           write("if (!alloc_failed) {");
           in();
           newLine();
           {
             write("processing_succeeded[i] = 1;");
             newLine();
             writeOutputUpdate("result", outParam, true);
           }
           out();
           newLine();
           write("}");
         }
      }
      out();
      newLine();
      write("}");

      out();
      newLine();
      writeln("}");
   }

   @Override public void writeThisRef() {
      write("this_ptr->");
   }

   @Override public boolean writeInstruction(Instruction _instruction) throws CodeGenException {
      if ((_instruction instanceof I_IUSHR) || (_instruction instanceof I_LUSHR)) {
         final BinaryOperator binaryInstruction = (BinaryOperator) _instruction;
         final Instruction parent = binaryInstruction.getParentExpr();
         boolean needsParenthesis = true;

         if (parent instanceof AssignToLocalVariable) {
            needsParenthesis = false;
         } else if (parent instanceof AssignToField) {
            needsParenthesis = false;
         } else if (parent instanceof AssignToArrayElement) {
            needsParenthesis = false;
         }
         if (needsParenthesis) {
            write("(");
         }

         if (binaryInstruction instanceof I_IUSHR) {
            write("((unsigned int)");
         } else {
            write("((unsigned long)");
         }
         writeInstruction(binaryInstruction.getLhs());
         write(")");
         write(" >> ");
         writeInstruction(binaryInstruction.getRhs());

         if (needsParenthesis) {
            write(")");
         }
         return false;
      } else {
         return super.writeInstruction(_instruction);
      }
   }

   public static class WriterAndKernel {
     public final KernelWriter writer;
     public final String kernel;

     public WriterAndKernel(KernelWriter writer, String kernel) {
       this.writer = writer;
       this.kernel = kernel;
     }
   }

   public static WriterAndKernel writeToString(Entrypoint _entrypoint,
         Collection<ScalaArrayParameter> params, boolean multiInput)
         throws CodeGenException, AparapiException, ClassNotFoundException {

      final StringBuilder openCLStringBuilder = new StringBuilder();
      final KernelWriter openCLWriter = new KernelWriter(multiInput) {
         private int writtenSinceLastNewLine = 0;
         private int writtenSinceLastExpression = 0;
         private int mark = -1;

         // @Override public void writeBeforeLastExpression(String _string) {
         //     openCLStringBuilder.insert(openCLStringBuilder.length() - writtenSinceLastExpression, _string);
         // }

         @Override public void writeBeforeCurrentLine(String _string) {
           char insertingAt = openCLStringBuilder.charAt(openCLStringBuilder.length() -
                   writtenSinceLastNewLine - 1);
           if (insertingAt != '\n') {
               throw new RuntimeException("Expected newline but found \"" +
                       insertingAt + "\"");
           }
           openCLStringBuilder.insert(openCLStringBuilder.length() -
               writtenSinceLastNewLine, _string + "\n");
         }

         // @Override public void markNewExpression() {
         //     writtenSinceLastExpression = 0;
         // }

         @Override public void markCurrentPosition() {
             if (mark != -1) {
                 throw new RuntimeException("Duplicated mark");
             }
             mark = openCLStringBuilder.length();
         }

         @Override public String eraseToMark() {
             if (mark == -1) {
                 throw new RuntimeException("Missing mark");
             }

             final String result = openCLStringBuilder.substring(mark);
             openCLStringBuilder.delete(mark, openCLStringBuilder.length());
             writtenSinceLastNewLine -= result.length();

             mark = -1;
             return result;
         }

         @Override public void write(String _string) {
            int lastNewLine = _string.lastIndexOf('\n');
            if (lastNewLine != -1) {
              writtenSinceLastNewLine = _string.length() - lastNewLine - 1;
            } else {
              writtenSinceLastNewLine += _string.length();
            }
            writtenSinceLastExpression += _string.length();
            openCLStringBuilder.append(_string);
         }
      };

      for (Map.Entry<String, String> entry : _entrypoint.getConfig().entrySet()) {
        openCLWriter.addConfig(entry.getKey(), entry.getValue());
      }

      try {
         openCLWriter.write(_entrypoint, params);
      } catch (final CodeGenException codeGenException) {
         throw codeGenException;
      }/* catch (final Throwable t) {
         throw new CodeGenException(t);
       }*/

      return (new WriterAndKernel(openCLWriter, openCLStringBuilder.toString()));
   }
}
