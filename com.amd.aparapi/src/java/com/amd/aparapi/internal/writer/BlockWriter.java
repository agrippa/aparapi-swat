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
import com.amd.aparapi.internal.instruction.BranchSet.LogicalExpressionNode;
import com.amd.aparapi.internal.instruction.InstructionSet.AccessInstanceField;
import com.amd.aparapi.internal.instruction.BranchSet.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.NameAndTypeEntry;

import java.util.*;

/**
 * Base abstract class for converting <code>Aparapi</code> IR to text.<br/>
 * 
 *   
 * @author gfrost
 *
 */

public abstract class BlockWriter{

   /*
    * A publically settable boolean flag to control the emitting of either CUDA
    * or OpenCL.
    */
   public static boolean emitOcl = true;

   public final static String arrayLengthMangleSuffix = "__javaArrayLength";

   public final static String arrayDimMangleSuffix = "__javaArrayDimension";

   public abstract void write(String _string);

   public abstract void writeBeforeCurrentLine(String _string);
   public abstract void markCurrentPosition();
   // public abstract void markNewExpression();
   // public abstract void writeBeforeLastExpression(String _string);
   public abstract String eraseToMark();

   public abstract String getAllocCheck();

   protected final Map<String, String> config = new HashMap<String, String>();

   public void addConfig(String key, String value) {
       config.put(key, value);
   }

   public void writeln(String _string) {
      write(_string);
      newLine();
   }

   public int indent = 0;

   public void in() {
      indent++;
   }

   public void out() {
      indent--;
   }

   public void newLine() {
      write("\n");
      for (int i = 0; i < indent; i++) {
         write("   ");
      }
   }

   public void writeConditionalBranch16(ConditionalBranch16 _branch16,
           boolean _invert) throws CodeGenException {

      if (_branch16 instanceof If) {
         final If iff = (If) _branch16;

         writeInstruction(iff.getLhs());
         write(_branch16.getOperator().getText(_invert));
         writeInstruction(iff.getRhs());
      } else if (_branch16 instanceof I_IFNULL) {
         final I_IFNULL iff = (I_IFNULL) _branch16;
         writeInstruction(iff.getFirstChild());

         if (_invert) {
            write(" != NULL");
         } else {
            write(" == NULL");
         }

      } else if (_branch16 instanceof I_IFNONNULL) {
         final I_IFNONNULL iff = (I_IFNONNULL) _branch16;
         writeInstruction(iff.getFirstChild());

         if (_invert) {
            write(" == NULL");
         } else {
            write(" != NULL");
         }
      } else if (_branch16 instanceof IfUnary) {
         final IfUnary branch16 = (IfUnary) _branch16;
         final Instruction comparison = branch16.getUnary();
         final ByteCode comparisonByteCode = comparison.getByteCode();
         final String comparisonOperator = _branch16.getOperator().getText(_invert);

         switch (comparisonByteCode) {
            case FCMPG:
            case DCMPG:
            case FCMPL:
            case DCMPL:
               if (Config.verboseComparitor) {
                  write("/* bytecode=" + comparisonByteCode.getName() + " invert=" + _invert + "*/");
               }
               writeInstruction(comparison.getFirstChild());
               write(comparisonOperator);
               writeInstruction(comparison.getLastChild());
               break;
            default:
               if (Config.verboseComparitor) {
                  write("/* default bytecode=" + comparisonByteCode.getName() + " invert=" + _invert + "*/");
               }
               writeInstruction(comparison);
               write(comparisonOperator);
               write("0");
         }
      }
   }

   public void writeComposite(CompositeInstruction instruction) throws CodeGenException {
      if (instruction instanceof CompositeArbitraryScopeInstruction) {
         newLine();

         writeBlock(instruction.getFirstChild(), null);
      } else if (instruction instanceof CompositeIfInstruction) {
         newLine();
         write("if (");
         final Instruction blockStart = writeConditional(instruction.getBranchSet());

         write(")");
         writeBlock(blockStart, null);
      } else if (instruction instanceof CompositeIfElseInstruction) {
         newLine();
         write("(");
         // write("if (");
         final Instruction blockStart = writeConditional(instruction.getBranchSet());
         write(") ? (");
         Instruction elseGoto = blockStart;
         while (!(elseGoto.isBranch() && elseGoto.asBranch().isUnconditional())) {
            elseGoto = elseGoto.getNextExpr();
         }
         if (BlockWriter.emitOcl) {
             writeBlock(blockStart, elseGoto);
         } else {
             Instruction lastInsn = getLastInstructionInSequence(blockStart, elseGoto);
             final String saveReturnType = KernelWriter.currentReturnType;
             KernelWriter.currentReturnType = inferType(lastInsn);
             write("([&] () -> " + KernelWriter.getTypenameForCurrentReturnType() + " {");
             final String insertAtEnd = (KernelWriter.currentReturnType.equals("V") ? "" : "return ");
             writeSequence(blockStart, elseGoto, insertAtEnd);
             writeln("; })()");
             KernelWriter.currentReturnType = saveReturnType;
         }
         write(") : (");
         // write(" else ");
         if (BlockWriter.emitOcl) {
             writeBlock(elseGoto.getNextExpr(), null);
         } else {
             Instruction lastInsn = getLastInstructionInSequence(blockStart, elseGoto);
             final String saveReturnType = KernelWriter.currentReturnType;
             KernelWriter.currentReturnType = inferType(lastInsn);
             write("([&] () -> " + KernelWriter.getTypenameForCurrentReturnType() + " {");
             final String insertAtEnd = (KernelWriter.currentReturnType.equals("V") ? "" : "return ");
             writeSequence(elseGoto.getNextExpr(), null, insertAtEnd);
             writeln("; })()");
             KernelWriter.currentReturnType = saveReturnType;
         }
         write(")");
      } else if (instruction instanceof CompositeForSunInstruction) {
         newLine();
         write("for (");
         Instruction topBranch = instruction.getFirstChild();
         if (topBranch instanceof AssignToLocalVariable) {
            writeInstruction(topBranch);
            topBranch = topBranch.getNextExpr();
         }
         write("; ");
         final BranchSet branchSet = instruction.getBranchSet();
         final Instruction blockStart = writeConditional(branchSet);

         final Instruction lastGoto = instruction.getLastChild();

         if (branchSet.getFallThrough() == lastGoto) {
            // empty body no delta!
            write(";){}");
         } else {
            final Instruction delta = lastGoto.getPrevExpr();
            write("; ");
            if (!(delta instanceof CompositeInstruction)) {
               writeInstruction(delta);
               write(")");
               writeBlock(blockStart, delta);
            } else {
               write("){");
               in();
               writeSequence(blockStart, delta);

               newLine();
               writeSequence(delta, delta.getNextExpr());
               out();
               newLine();
               write("}");

            }
         }

      } else if (instruction instanceof CompositeWhileInstruction) {
         newLine();
         write("while (");
         final BranchSet branchSet = instruction.getBranchSet();
         final Instruction blockStart = writeConditional(branchSet);
         write(")");
         final Instruction lastGoto = instruction.getLastChild();
         writeBlock(blockStart, lastGoto);

      } else if (instruction instanceof CompositeEmptyLoopInstruction) {
         newLine();
         write("for (");
         Instruction topBranch = instruction.getFirstChild();
         if (topBranch instanceof AssignToLocalVariable) {
            writeInstruction(topBranch);
            topBranch = topBranch.getNextExpr();
         }
         write("; ");
         writeConditional(instruction.getBranchSet());
         write(";){}");

      } else if (instruction instanceof CompositeForEclipseInstruction) {
         newLine();
         write("for (");
         Instruction topGoto = instruction.getFirstChild();
         if (topGoto instanceof AssignToLocalVariable) {
            writeInstruction(topGoto);
            topGoto = topGoto.getNextExpr();
         }
         write("; ");
         Instruction last = instruction.getLastChild();
         while (last.getPrevExpr().isBranch()) {
            last = last.getPrevExpr();
         }
         writeConditional(instruction.getBranchSet(), true);
         write("; ");
         final Instruction delta = last.getPrevExpr();
         if (!(delta instanceof CompositeInstruction)) {
            writeInstruction(delta);
            write(")");
            writeBlock(topGoto.getNextExpr(), delta);
         } else {
            write("){");
            in();
            writeSequence(topGoto.getNextExpr(), delta);

            newLine();
            writeSequence(delta, delta.getNextExpr());
            out();
            newLine();
            write("}");

         }

      } else if (instruction instanceof CompositeDoWhileInstruction) {
         newLine();
         write("do");
         Instruction blockStart = instruction.getFirstChild();
         Instruction blockEnd = instruction.getLastChild();
         writeBlock(blockStart, blockEnd);
         write("while(");
         writeConditional(((CompositeInstruction) instruction).getBranchSet(), true);
         write(");");
         newLine();
      }
   }

   protected Instruction getLastInstructionInSequence(Instruction _first, Instruction _last) {
       Instruction last = null;
       for (Instruction instruction = _first; instruction != _last; instruction = instruction.getNextExpr()) {
           last = instruction;
      }
       return last;
   }

   public void writeSequence(Instruction _first, Instruction _last, String insertBeforeLast) throws CodeGenException {
      for (Instruction instruction = _first; instruction != _last; instruction = instruction.getNextExpr()) {
         if (instruction instanceof CompositeInstruction) {
            if (instruction.getNextExpr() == _last) {
                write(insertBeforeLast);
            }
            writeComposite((CompositeInstruction) instruction);
         } else if (!instruction.getByteCode().equals(ByteCode.NONE)) {
            newLine();
            if (instruction.getNextExpr() == _last) {
                write(insertBeforeLast);
            }
            boolean writeCheck = writeInstruction(instruction);
            write(";");
            if (writeCheck) {
              write(getAllocCheck());
            }
         }
      }
   }

   public void writeSequence(Instruction _first, Instruction _last) throws CodeGenException {
       writeSequence(_first, _last, "");
   }

   protected void writeGetterBlock(FieldEntry accessorVariableFieldEntry) {
      write("{");
      in();
      newLine();
      write("return this_ptr->");
      write(accessorVariableFieldEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
      write(";");
      out();
      newLine();

      write("}");
   }

   public void writeBlock(Instruction _first, Instruction _last) throws CodeGenException {
      writeln("{");
      in();
      writeSequence(_first, _last);
      out();
      newLine();

      write("}");
   }

   public Instruction writeConditional(BranchSet _branchSet) throws CodeGenException {
      return (writeConditional(_branchSet, false));
   }

   public Instruction writeConditional(BranchSet _branchSet, boolean _invert) throws CodeGenException {

      final LogicalExpressionNode logicalExpression = _branchSet.getLogicalExpression();
      write(_invert ? logicalExpression : logicalExpression.cloneInverted());
      return (_branchSet.getLast().getNextExpr());
   }

   public void write(LogicalExpressionNode _node) throws CodeGenException {
      if (_node instanceof SimpleLogicalExpressionNode) {
         final SimpleLogicalExpressionNode sn = (SimpleLogicalExpressionNode) _node;

         writeConditionalBranch16((ConditionalBranch16) sn.getBranch(), sn.isInvert());
      } else {
         final CompoundLogicalExpressionNode ln = (CompoundLogicalExpressionNode) _node;
         boolean needParenthesis = false;
         final CompoundLogicalExpressionNode parent = (CompoundLogicalExpressionNode) ln.getParent();
         if (parent != null) {
            if (!ln.isAnd() && parent.isAnd()) {
               needParenthesis = true;
            }
         }
         if (needParenthesis) {

            write("(");
         }
         write(ln.getLhs());
         write(ln.isAnd() ? " && " : " || ");
         write(ln.getRhs());
         if (needParenthesis) {

            write(")");
         }
      }
   }

   public String convertType(String _typeDesc, boolean useClassModel) {
      return (_typeDesc);
   }

   public String convertCast(String _cast) {
      // Strip parens off cast
      //System.out.println("cast = " + _cast);
      final String raw = convertType(_cast.substring(1, _cast.length() - 1), false);
      return ("(" + raw + ")");
   }

   private static boolean isBroadcastedObjectArray(Instruction target) {
     if (!(target instanceof I_CHECKCAST)) {
         return false;
     }
     I_CHECKCAST cast = (I_CHECKCAST)target;
     if (!(cast.getPrevPC() instanceof I_INVOKEVIRTUAL)) {
         return false;
     }
     I_INVOKEVIRTUAL call = (I_INVOKEVIRTUAL)cast.getPrevPC();
     if (!call.getConstantPoolMethodEntry().toString().equals(KernelWriter.BROADCAST_VALUE_SIG)) {
         return false;
     }
     if (!(call.getPrevPC() instanceof I_GETFIELD)) {
         return false;
     }

     String typeName = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
     if (!typeName.startsWith("[")) {
         throw new RuntimeException("All broadcasted variables should be array-typed");
     }
     typeName = typeName.substring(1);
     return typeName.startsWith("L");
   }

   public static String getAnonymousVariableName(MethodModel method, int localVariableIndex) {
       return method.getName() + "__tmp" + localVariableIndex;
   }

   protected String inferConstructorType(ConstructorCall call) {
     StringBuilder result = new StringBuilder();
     I_INVOKESPECIAL invokeSpecial = call.getInvokeSpecial();
     int nargs = invokeSpecial.getStackConsumeCount() - 1;
     for (int a = 0; a < nargs; a++) {
         Instruction arg = invokeSpecial.getArg(a);

         final String s;
         if (arg instanceof I_CHECKCAST) {
             I_CHECKCAST cast = (I_CHECKCAST)arg;
             s = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
         } else if (arg instanceof ConstructorCall) {
             ConstructorCall constr = (ConstructorCall)arg;
             s = inferConstructorType(constr);
         } else if (arg instanceof I_INVOKESTATIC) {
             I_INVOKESTATIC stat = (I_INVOKESTATIC)arg;
             String name = stat.getConstantPoolMethodEntry().toString();
             if (name.equals("scala/runtime/BoxesRunTime.boxToInteger(I)Ljava/lang/Integer;")) {
                 s = "I";
             } else if (name.equals("scala/runtime/BoxesRunTime.boxToFloat(F)Ljava/lang/Float;")) {
                 s = "F";
             } else if (name.equals("scala/runtime/BoxesRunTime.boxToDouble(D)Ljava/lang/Double;")) {
                 s = "D";
             } else {
                 throw new RuntimeException("Unsupported: " + name);
             }
         } else if (arg instanceof I_I2F) {
             s = "F";
         } else if (arg instanceof I_FADD || arg instanceof I_FDIV ||
                 arg instanceof I_FMUL || arg instanceof I_FSUB) {
             s = "F";
         } else if (arg instanceof I_DADD || arg instanceof I_DDIV ||
                 arg instanceof I_DMUL || arg instanceof I_DSUB) {
             s = "D";
         } else if (arg instanceof I_IADD || arg instanceof I_IDIV ||
                 arg instanceof I_IMUL || arg instanceof I_ISUB) {
             s = "I";
         } else if (arg instanceof I_FCONST_0 || arg instanceof I_FCONST_1 ||
                 arg instanceof I_FCONST_2) {
             s = "F";
         } else if (arg instanceof I_LDC_W || arg instanceof I_LDC) {
             String typeName = ((Constant)arg).getValue().getClass().getName();
             if (typeName.equals("java.lang.Float")) {
                 s = "F";
             } else if (typeName.equals("java.lang.Integer")) {
                 s = "I";
             } else if (typeName.equals("java.lang.Double")) {
                 s = "D";
             } else {
                 throw new RuntimeException(typeName);
             }
         } else if (arg instanceof I_INVOKEVIRTUAL) {
             MethodEntryInfo info = ((I_INVOKEVIRTUAL)arg).getConstantPoolMethodEntry();
             String desc = info.getMethodSig();
             String returnType = desc.substring(desc.lastIndexOf(")") + 1);
             s = returnType;
         } else if (arg instanceof I_ILOAD_0 || arg instanceof I_ILOAD_1 ||
                 arg instanceof I_ILOAD_2 || arg instanceof I_ILOAD_3 || arg instanceof I_ILOAD) {
             s = "I";
         } else if (arg instanceof I_DLOAD_0 || arg instanceof I_DLOAD_1 ||
                 arg instanceof I_DLOAD_2 || arg instanceof I_DLOAD_3 || arg instanceof I_DLOAD) {
             s = "D";
         } else if (arg instanceof I_FLOAD_0 || arg instanceof I_FLOAD_1 ||
                 arg instanceof I_FLOAD_2 || arg instanceof I_FLOAD_3 || arg instanceof I_FLOAD) {
             s = "F";
         } else if (arg instanceof I_ICONST_M1 || arg instanceof I_ICONST_0 ||
                 arg instanceof I_ICONST_1 || arg instanceof I_ICONST_2 ||
                 arg instanceof I_ICONST_3 || arg instanceof I_ICONST_4 ||
                 arg instanceof I_ICONST_5) {
             s = "I";
         } else if (arg instanceof I_FCONST_0 || arg instanceof I_FCONST_1 ||
                 arg instanceof I_FCONST_2) {
             s = "F";
         } else if (arg instanceof I_DCONST_0 || arg instanceof I_DCONST_1) {
             s = "D";
         } else {
             throw new RuntimeException("Unable to do type inference on " + arg);
         }

         if (a != 0) result.append(",");
         result.append(s);
     }
     String containerClass = invokeSpecial.getConstantPoolMethodEntry().getClassName();
     if (containerClass.startsWith("scala/Tuple2")) {
         containerClass = "scala/Tuple2";
     }
     return containerClass + "(" + result.toString() + ")";
   }

   protected String inferType(Instruction insn) {
       boolean handled = false;

       if (insn instanceof I_INVOKESPECIAL) {
           I_INVOKESPECIAL invokeSpecial = (I_INVOKESPECIAL)insn;
           MethodEntryInfo entry = invokeSpecial.getConstantPoolMethodEntry();
           final String name = entry.getMethodName();
           if (name.equals("<init>")) {
               // A constructor
               ConstructorCall call = new ConstructorCall(
                       insn.getMethod(), invokeSpecial, null);
               return inferConstructorType(call);
           } else {
               return entry.getMethodSig();
           }
       } else if (insn instanceof AccessArrayElement) {
           AccessArrayElement access = (AccessArrayElement)insn;
           Instruction arr = access.getArrayRef();
           String arrayType = inferType(arr);
           if (!arrayType.startsWith("[")) {
               throw new RuntimeException("Unexpected array type " + arrayType);
           }
           return arrayType.substring(1);
       } else if (insn instanceof ConstructorCall) {
           return inferConstructorType((ConstructorCall)insn);
       } else if (insn instanceof CompositeArbitraryScopeInstruction) {
           CompositeArbitraryScopeInstruction scope = (CompositeArbitraryScopeInstruction)insn;
           return inferType(getLastInstructionInSequence(scope.getFirstChild(), null));
       } else if (insn instanceof CompositeIfElseInstruction) {
           CompositeIfElseInstruction comp = (CompositeIfElseInstruction)insn;
           Instruction firstBlockEnd = comp.getBranchSet().getLast().getNextExpr();
           while (!(firstBlockEnd.isBranch() && firstBlockEnd.asBranch().isUnconditional())) {
              firstBlockEnd = firstBlockEnd.getNextExpr();
           }
           firstBlockEnd = firstBlockEnd.getPrevExpr();
           return inferType(firstBlockEnd);
       } else if (insn instanceof LocalVariableConstIndexLoad) {
           LocalVariableConstIndexLoad ld = (LocalVariableConstIndexLoad)insn;
           LocalVariableInfo info = ld.getLocalVariableInfo();
           return info.getVariableDescriptor();
       } else if (insn instanceof I_IADD || insn instanceof I_IMUL) {
           return "I";
       } else if (insn instanceof I_FADD || insn instanceof I_D2F) {
           return "F";
       } else if (insn instanceof I_DDIV || insn instanceof I_DADD || insn instanceof I_DMUL) {
           return "D";
       } else if (insn instanceof I_DASTORE) {
           return "V";
       } else if (insn instanceof I_CHECKCAST) {
           I_CHECKCAST cast = (I_CHECKCAST)insn;
           return cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
       } else if (insn instanceof I_GETFIELD) {
           I_GETFIELD get = (I_GETFIELD)insn;
           return get.getConstantPoolFieldEntry().getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
       } else if (insn instanceof I_INVOKEVIRTUAL) {
           I_INVOKEVIRTUAL call = (I_INVOKEVIRTUAL)insn;
           MethodEntryInfo info = call.getConstantPoolMethodEntry();
           String desc = info.getMethodSig();
           return desc.substring(desc.lastIndexOf(")") + 1);
       }

       throw new RuntimeException("Unsupported type inference on " + insn.getClass().getName());
   }

   public boolean writeInstruction(Instruction _instruction) throws CodeGenException {
      boolean writeCheck = false;

      if (_instruction instanceof CompositeIfElseInstruction) {
         write("(");
         final Instruction blockStart = writeConditional(((CompositeIfElseInstruction)_instruction).getBranchSet());
         write(") ? (");
         Instruction elseGoto = blockStart;
         while (!(elseGoto.isBranch() && elseGoto.asBranch().isUnconditional())) {
            elseGoto = elseGoto.getNextExpr();
         }
         if (BlockWriter.emitOcl) {
             writeBlock(blockStart, elseGoto);
         } else {
             Instruction lastInsn = getLastInstructionInSequence(blockStart, elseGoto);
             final String saveReturnType = KernelWriter.currentReturnType;
             KernelWriter.currentReturnType = inferType(lastInsn);
             write("([&] () -> " + KernelWriter.getTypenameForCurrentReturnType() + " {");
             final String insertAtEnd = (KernelWriter.currentReturnType.equals("V") ? "" : "return ");
             writeSequence(blockStart, elseGoto, insertAtEnd);
             write("; })()");
             KernelWriter.currentReturnType = saveReturnType;
         }
         write(") : (");
         if (BlockWriter.emitOcl) {
             writeBlock(elseGoto.getNextExpr(), null);
         } else {
             Instruction lastInsn = getLastInstructionInSequence(blockStart, elseGoto);
             final String saveReturnType = KernelWriter.currentReturnType;
             KernelWriter.currentReturnType = inferType(lastInsn);
             write("([&] () -> " + KernelWriter.getTypenameForCurrentReturnType() + " {");
             final String insertAtEnd = (KernelWriter.currentReturnType.equals("V") ? "" : "return ");
             writeSequence(elseGoto.getNextExpr(), null, insertAtEnd);
             write("; })()");
             KernelWriter.currentReturnType = saveReturnType;
         }
         write(")");
      } else
      /* if (_instruction instanceof CompositeIfElseInstruction) {
         write("(");
         final Instruction lhs = writeConditional(((CompositeInstruction) _instruction).getBranchSet());
         write(")?");
         writeInstruction(lhs);
         write(":");
         writeInstruction(lhs.getNextExpr().getNextExpr());
      } else */ if (_instruction instanceof CompositeInstruction) {
         writeComposite((CompositeInstruction) _instruction);

      } else if (_instruction instanceof AssignToLocalVariable) {
         final AssignToLocalVariable assignToLocalVariable = (AssignToLocalVariable) _instruction;

         final LocalVariableInfo localVariableInfo = assignToLocalVariable.getLocalVariableInfo();
         if (assignToLocalVariable.isDeclaration()) {
            final String descriptor = localVariableInfo.getVariableDescriptor();
            // Arrays always map to __global arrays
            if (BlockWriter.emitOcl && (descriptor.startsWith("[") || descriptor.startsWith("L"))) {
               write(" __global ");
            }

            String localType = convertType(descriptor, true);
            if (descriptor.startsWith("L")) {
              localType = localType.replace('.', '_');
            }
            write(localType);

            if (descriptor.startsWith("L")) {
              write("*"); // All local assigns to object-typed variables should be a constructor
            }
         }
         
         if (localVariableInfo == null) {
             /*
              * Assume that this is a temporary, anonymous local that is stored
              * to once and loaded from once. Used as a temporary storage
              * location for some short-lived value which does not have a
              * corresponding name in the user program.
              */
             String varname = getAnonymousVariableName(_instruction.getMethod(),
                     assignToLocalVariable.getLocalVariableTableIndex());
             Instruction src = _instruction.getFirstChild();
             if (src instanceof AccessLocalVariable) {
                 LocalVariableInfo srcInfo = ((AccessLocalVariable)src).getLocalVariableInfo();
                 final String descriptor = srcInfo.getVariableDescriptor();
                 // Arrays always map to __global arrays
                 if (BlockWriter.emitOcl && (descriptor.startsWith("[") || descriptor.startsWith("L"))) {
                    write(" __global ");
                 }

                 String localType = convertType(descriptor, true);
                 if (descriptor.startsWith("L")) {
                   localType = localType.replace('.', '_') + "*";
                 }
                 write(localType);
             } else {
                 throw new RuntimeException(src.toString());
             }
             write(" " + varname + " = ");
         } else {
            write(localVariableInfo.getVariableName() + " = ");
         }

         for (Instruction operand = _instruction.getFirstChild(); operand != null; operand = operand.getNextExpr()) {
            writeInstruction(operand);
         }

      } else if (_instruction instanceof AssignToArrayElement) {
         final AssignToArrayElement arrayAssignmentInstruction = (AssignToArrayElement) _instruction;
         writeInstruction(arrayAssignmentInstruction.getArrayRef());
         write("[");
         writeInstruction(arrayAssignmentInstruction.getArrayIndex());
         write("]");
         write(" ");
         write(" = ");
         writeInstruction(arrayAssignmentInstruction.getValue());
      } else if (_instruction instanceof AccessArrayElement) {

         //we're getting an element from an array
         //if the array is a primitive then we just return the value
         //so the generated code looks like
         //arrayName[arrayIndex];
         //but if the array is an object, or multidimensional array, then we want to return
         //a pointer to our index our position in the array.  The code will look like
         //&(arrayName[arrayIndex * this->arrayNameLen_dimension]
         //
         final AccessArrayElement arrayLoadInstruction = (AccessArrayElement) _instruction;

         //object array, get address
         boolean isMultiDimensional = arrayLoadInstruction instanceof I_AALOAD && isMultiDimensionalArray(arrayLoadInstruction);
         boolean broadcastedObject =
             isBroadcastedObjectArray(arrayLoadInstruction.getArrayRef());
         if (isMultiDimensional || broadcastedObject) {
            write("(&");
         }
         Instruction arrayRef = arrayLoadInstruction.getArrayRef();
         final boolean isSparseVectorAccess =
             Entrypoint.isSparseVectorIndicesOrValues(arrayRef);
         writeInstruction(arrayRef);
         write("[");
         if (isSparseVectorAccess) {
             Instruction target = ((I_INVOKEVIRTUAL)arrayRef).getInstanceReference();
             writeInstruction(target);
             write("->tiling * (");
         }
         writeInstruction(arrayLoadInstruction.getArrayIndex());
         if (isSparseVectorAccess) {
             write(")");
         }

         //object array, find the size of each object in the array
         //for 2D arrays, this size is the size of a row.
         if (isMultiDimensional) {
            int dim = 0;
            Instruction load = arrayLoadInstruction.getArrayRef();
            while (load instanceof I_AALOAD) {
               load = load.getFirstChild();
               dim++;
            }

            NameAndTypeEntry nameAndTypeEntry = ((AccessInstanceField) load).getConstantPoolFieldEntry().getNameAndTypeEntry();
            if (isMultiDimensionalArray(nameAndTypeEntry)) {
               String arrayName = nameAndTypeEntry.getNameUTF8Entry().getUTF8();
               write(" * this_ptr->" + arrayName + arrayDimMangleSuffix + dim);
            }
         }

         write("]");

         //object array, close parentheses
         if (isMultiDimensional || broadcastedObject) {
            write(")");
         }
      } else if (_instruction instanceof AccessField) {
         final AccessField accessField = (AccessField) _instruction;
         if (accessField instanceof AccessInstanceField) {
            Instruction accessInstanceField = ((AccessInstanceField) accessField).getInstance();
            if (accessInstanceField instanceof CloneInstruction) {
               accessInstanceField = ((CloneInstruction) accessInstanceField).getReal();
            }
            if (accessInstanceField != null && !(accessInstanceField instanceof I_ALOAD_0)) {
               writeInstruction(accessInstanceField);
               write(".");
            } else {
               writeThisRef();
            }
         }
         write(accessField.getConstantPoolFieldEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8().replace('$', '_'));

      } else if (_instruction instanceof I_ARRAYLENGTH) {

         //getting the length of an array.
         //if this is a primitive array, then this is trivial
         //if we're getting an object array, then we need to find what dimension
         //we're looking at
         int dim = 0;
         Instruction load = _instruction.getFirstChild();
         while (load instanceof I_AALOAD) {
            load = load.getFirstChild();
            dim++;
         }
         if (load instanceof AccessInstanceField) {
            NameAndTypeEntry nameAndTypeEntry = ((AccessInstanceField) load)
                .getConstantPoolFieldEntry().getNameAndTypeEntry();
            final String arrayName = nameAndTypeEntry.getNameUTF8Entry().getUTF8();
            String dimSuffix = isMultiDimensionalArray(nameAndTypeEntry) ?
                Integer.toString(dim) : "";
            write("this_ptr->" + arrayName.replace('$', '_') + arrayLengthMangleSuffix + dimSuffix);
         } else if (load instanceof LocalVariableConstIndexLoad) {
             assert(dim == 1);
             final String arrayName = ((LocalVariableConstIndexLoad)load)
                 .getLocalVariableInfo().getVariableName();
             write(arrayName.replace('$', '_') + BlockWriter.arrayLengthMangleSuffix);
         }
      } else if (_instruction instanceof AssignToField) {
         final AssignToField assignedField = (AssignToField) _instruction;

         if (assignedField instanceof AssignToInstanceField) {
            final Instruction accessInstanceField = ((AssignToInstanceField) assignedField).getInstance().getReal();

            if (!(accessInstanceField instanceof I_ALOAD_0)) {
               writeInstruction(accessInstanceField);
               write(".");
            } else {
               writeThisRef();
            }
         }
         write(assignedField.getConstantPoolFieldEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8().replace('$', '_'));
         write("=");
         writeInstruction(assignedField.getValueToAssign());
      } else if (_instruction instanceof Constant<?>) {
         final Constant<?> constantInstruction = (Constant<?>) _instruction;
         final Object value = constantInstruction.getValue();

         if (value instanceof Float) {

            final Float f = (Float) value;
            if (f.isNaN()) {
               write("NAN");
            } else if (f.isInfinite()) {
               if (f < 0) {
                  write("-");
               }
               write("INFINITY");
            } else {
               write(value.toString());
               write("f");
            }
         } else if (value instanceof Double) {

            final Double d = (Double) value;
            if (d.isNaN()) {
               write("NAN");
            } else if (d.isInfinite()) {
               if (d < 0) {
                  write("-");
               }
               write("INFINITY");
            } else {
               write(value.toString());
            }
         } else if (constantInstruction instanceof I_ACONST_NULL) {
             write("NULL");
         } else {
            write(value.toString());
            if (value instanceof Long) {
               write("L");
            }
         }

      } else if (_instruction instanceof AccessLocalVariable) {
         final AccessLocalVariable localVariableLoadInstruction = (AccessLocalVariable) _instruction;
         final LocalVariableInfo localVariable = localVariableLoadInstruction.getLocalVariableInfo();
         if (localVariable == null) {
             String varname = getAnonymousVariableName(_instruction.getMethod(),
                     localVariableLoadInstruction.getLocalVariableTableIndex());
             write(varname);
         } else {
             write(localVariable.getVariableName());
         }
      } else if (_instruction instanceof I_IINC) {
         final I_IINC location = (I_IINC) _instruction;
         final LocalVariableInfo localVariable = location.getLocalVariableInfo();
         final int adjust = location.getAdjust();

         write(localVariable.getVariableName());
         if (adjust == 1) {
            write("++");
         } else if (adjust == -1) {
            write("--");
         } else if (adjust > 1) {
            write("+=" + adjust);
         } else if (adjust < -1) {
            write("-=" + (-adjust));
         }
      } else if (_instruction instanceof BinaryOperator) {
         final BinaryOperator binaryInstruction = (BinaryOperator) _instruction;
         final Instruction parent = binaryInstruction.getParentExpr();
         boolean needsParenthesis = true;

         if (parent instanceof AssignToLocalVariable) {
            needsParenthesis = false;
         } else if (parent instanceof AssignToField) {
            needsParenthesis = false;
         } else if (parent instanceof AssignToArrayElement) {
            needsParenthesis = false;
         } else {
            /**
                        if (parent instanceof BinaryOperator) {
                           BinaryOperator parentBinaryOperator = (BinaryOperator) parent;
                           if (parentBinaryOperator.getOperator().ordinal() > binaryInstruction.getOperator().ordinal()) {
                              needsParenthesis = false;
                           }
                        }
            **/
         }

         if (needsParenthesis) {
            write("(");
         }

         writeInstruction(binaryInstruction.getLhs());

         write(" " + binaryInstruction.getOperator().getText() + " ");
         writeInstruction(binaryInstruction.getRhs());

         if (needsParenthesis) {
            write(")");
         }

      } else if (_instruction instanceof CastOperator) {
         final CastOperator castInstruction = (CastOperator) _instruction;
         //  write("(");
         write(convertCast(castInstruction.getOperator().getText()));

         writeInstruction(castInstruction.getUnary());
         //    write(")");
      } else if (_instruction instanceof UnaryOperator) {
         final UnaryOperator unaryInstruction = (UnaryOperator) _instruction;
         //   write("(");
         write(unaryInstruction.getOperator().getText());

         writeInstruction(unaryInstruction.getUnary());
         //   write(")");
      } else if (_instruction instanceof Return) {
          writeReturn((Return) _instruction);
      } else if (_instruction instanceof MethodCall) {
         final MethodCall methodCall = (MethodCall) _instruction;

         final MethodEntryInfo methodEntry = methodCall.getConstantPoolMethodEntry();

         writeCheck = writeMethod(methodCall, methodEntry);
      } else if (_instruction.getByteCode().equals(ByteCode.CLONE)) {
         final CloneInstruction cloneInstruction = (CloneInstruction) _instruction;
         writeInstruction(cloneInstruction.getReal());
      } else if (_instruction.getByteCode().equals(ByteCode.INCREMENT)) {
         final IncrementInstruction incrementInstruction = (IncrementInstruction) _instruction;

         if (incrementInstruction.isPre()) {
            if (incrementInstruction.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }

         writeInstruction(incrementInstruction.getFieldOrVariableReference());
         if (!incrementInstruction.isPre()) {
            if (incrementInstruction.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }
      } else if (_instruction.getByteCode().equals(ByteCode.MULTI_ASSIGN)) {
         final MultiAssignInstruction multiAssignInstruction = (MultiAssignInstruction) _instruction;
         AssignToLocalVariable from = (AssignToLocalVariable) multiAssignInstruction.getFrom();
         final AssignToLocalVariable last = (AssignToLocalVariable) multiAssignInstruction.getTo();
         final Instruction common = multiAssignInstruction.getCommon();
         final Stack<AssignToLocalVariable> stack = new Stack<AssignToLocalVariable>();

         while (from != last) {
            stack.push(from);
            from = (AssignToLocalVariable) ((Instruction) from).getNextExpr();
         }

         for (AssignToLocalVariable alv = stack.pop(); alv != null; alv = stack.size() > 0 ? stack.pop() : null) {

            final LocalVariableInfo localVariableInfo = alv.getLocalVariableInfo();
            if (alv.isDeclaration()) {
               write(convertType(localVariableInfo.getVariableDescriptor(), true));
            }
            if (localVariableInfo == null) {
               throw new CodeGenException("outOfScope" + _instruction.getThisPC() + " = ");
            } else {
               write(localVariableInfo.getVariableName() + " = ");
            }

         }
         writeInstruction(common);
      } else if (_instruction.getByteCode().equals(ByteCode.INLINE_ASSIGN)) {
         final InlineAssignInstruction inlineAssignInstruction = (InlineAssignInstruction) _instruction;
         final AssignToLocalVariable assignToLocalVariable = inlineAssignInstruction.getAssignToLocalVariable();

         final LocalVariableInfo localVariableInfo = assignToLocalVariable.getLocalVariableInfo();
         if (assignToLocalVariable.isDeclaration()) {
            // this is bad! we need a general way to hoist up a required declaration
            throw new CodeGenException("/* we can't declare this " + convertType(localVariableInfo.getVariableDescriptor(), true)
                  + " here */");
         }
         write(localVariableInfo.getVariableName());
         write("=");
         writeInstruction(inlineAssignInstruction.getRhs());
      } else if (_instruction.getByteCode().equals(ByteCode.FIELD_ARRAY_ELEMENT_ASSIGN)) {
         final FieldArrayElementAssign inlineAssignInstruction = (FieldArrayElementAssign) _instruction;
         final AssignToArrayElement arrayAssignmentInstruction = inlineAssignInstruction.getAssignToArrayElement();

         writeInstruction(arrayAssignmentInstruction.getArrayRef());
         write("[");
         writeInstruction(arrayAssignmentInstruction.getArrayIndex());
         write("]");
         write(" ");
         write(" = ");

         writeInstruction(inlineAssignInstruction.getRhs());
      } else if (_instruction.getByteCode().equals(ByteCode.FIELD_ARRAY_ELEMENT_INCREMENT)) {

         final FieldArrayElementIncrement fieldArrayElementIncrement = (FieldArrayElementIncrement) _instruction;
         final AssignToArrayElement arrayAssignmentInstruction = fieldArrayElementIncrement.getAssignToArrayElement();
         if (fieldArrayElementIncrement.isPre()) {
            if (fieldArrayElementIncrement.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }
         writeInstruction(arrayAssignmentInstruction.getArrayRef());

         write("[");
         writeInstruction(arrayAssignmentInstruction.getArrayIndex());
         write("]");
         if (!fieldArrayElementIncrement.isPre()) {
            if (fieldArrayElementIncrement.isInc()) {
               write("++");
            } else {
               write("--");
            }
         }

      } else if (_instruction.getByteCode().equals(ByteCode.NONE)) {
         // we are done
      } else if (_instruction instanceof Branch) {
         throw new CodeGenException(String.format("%s -> %04d", _instruction.getByteCode().toString().toLowerCase(),
               ((Branch) _instruction).getTarget().getThisPC()));
      } else if (_instruction instanceof I_POP) {
         //POP discarded void call return?
         writeInstruction(_instruction.getFirstChild());
      } else if (_instruction instanceof ConstructorCall) {
        final ConstructorCall call = (ConstructorCall)_instruction;
        writeConstructorCall(call);
      } else if (_instruction instanceof I_CHECKCAST) {
        // Do nothing
        I_CHECKCAST checkCast = (I_CHECKCAST)_instruction;
        writeInstruction(checkCast.getPrevPC());
      } else if (_instruction instanceof I_NEWARRAY) {
        I_NEWARRAY newArray = (I_NEWARRAY)_instruction;
        writePrimitiveArrayAlloc(newArray);
      } else if (_instruction instanceof New) {
        // Skip it?
      } else {
         System.err.println(_instruction.toString());
         throw new CodeGenException(String.format("%s", _instruction.getByteCode().toString().toLowerCase()));
      }

      return writeCheck;
   }

   public abstract void writeConstructorCall(ConstructorCall call) throws CodeGenException;
   public abstract void writeReturn(Return ret) throws CodeGenException;
   public abstract void writePrimitiveArrayAlloc(I_NEWARRAY newArray) throws CodeGenException;

   private boolean isMultiDimensionalArray(NameAndTypeEntry nameAndTypeEntry) {
      return nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8().startsWith("[[");
   }

   private boolean isObjectArray(NameAndTypeEntry nameAndTypeEntry) {
      return nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8().startsWith("[L");
   }

   private boolean isMultiDimensionalArray(final AccessArrayElement arrayLoadInstruction) {
      AccessInstanceField accessInstanceField = getUltimateInstanceFieldAccess(arrayLoadInstruction);
      return isMultiDimensionalArray(accessInstanceField.getConstantPoolFieldEntry().getNameAndTypeEntry());
   }

   private boolean isObjectArray(final AccessArrayElement arrayLoadInstruction) {
      AccessInstanceField accessInstanceField = getUltimateInstanceFieldAccess(arrayLoadInstruction);
      return isObjectArray(accessInstanceField.getConstantPoolFieldEntry().getNameAndTypeEntry());
   }

   private AccessInstanceField getUltimateInstanceFieldAccess(final AccessArrayElement arrayLoadInstruction) {
      Instruction load = arrayLoadInstruction.getArrayRef();
      while (load instanceof I_AALOAD) {
         load = load.getFirstChild();
      }

      if (load instanceof I_CHECKCAST &&
              load.getPrevPC() instanceof I_INVOKEVIRTUAL &&
              ((I_INVOKEVIRTUAL)load.getPrevPC()).getConstantPoolMethodEntry().toString().equals(
                    KernelWriter.BROADCAST_VALUE_SIG)) {
        load = load.getPrevPC().getPrevPC();
      }
      return (AccessInstanceField) load;
   }

   public boolean writeMethod(MethodCall _methodCall, MethodEntryInfo _methodEntry) throws CodeGenException {
      boolean noCL = _methodEntry.getOwnerClassModel().getNoCLMethods()
            .contains(_methodEntry.getMethodName());
      if (noCL) {
         return false;
      }

      if (_methodCall instanceof VirtualMethodCall) {
         final Instruction instanceInstruction = ((VirtualMethodCall) _methodCall).getInstanceReference();
         if (!(instanceInstruction instanceof I_ALOAD_0)) {
            writeInstruction(instanceInstruction);
            write(".");
         } else {
            writeThisRef();
         }
      }
      final int argc = _methodEntry.getStackConsumeCount();
      write(_methodEntry.getMethodName());
      write("(");

      for (int arg = 0; arg < argc; arg++) {
         if (arg != 0) {
            write(", ");
         }
         writeInstruction(_methodCall.getArg(arg));
      }
      write(")");

      return false;
   }

   public void writeThisRef() {
      write("this_ptr.");
   }

   public void writeMethodBody(MethodModel _methodModel) throws CodeGenException {
      if (_methodModel.isGetter() && !_methodModel.isNoCL()) {
         FieldEntry accessorVariableFieldEntry = _methodModel.getAccessorVariableFieldEntry();
         writeGetterBlock(accessorVariableFieldEntry);
      } else {
         writeBlock(_methodModel.getExprHead(), null);
      }
   }

   public abstract void write(Entrypoint entryPoint, Collection<ScalaArrayParameter> params) throws CodeGenException, ClassNotFoundException;
}
