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
package com.amd.aparapi.internal.model;

import com.amd.aparapi.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.MethodReferenceEntry.*;
import com.amd.aparapi.internal.util.*;

import com.amd.aparapi.internal.writer.KernelWriter;
import com.amd.aparapi.internal.writer.ScalaParameter;
import com.amd.aparapi.internal.writer.ScalaArrayParameter;
import com.amd.aparapi.internal.model.HardCodedClassModels.HardCodedClassModelMatcher;
import com.amd.aparapi.internal.model.HardCodedClassModels.DescMatcher;
import com.amd.aparapi.internal.model.HardCodedClassModels.ShouldNotCallMatcher;
import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

public class Entrypoint implements Cloneable {

   private static final String[] EMPTY_STRING_ARRAY = new String[0];

   private static Logger logger = Logger.getLogger(Config.getLoggerName());

   private final List<ClassModel.ClassModelField> referencedClassModelFields =
       new ArrayList<ClassModel.ClassModelField>();
   private final List<Boolean> isBroadcasted = new ArrayList<Boolean>();

   private final List<Field> referencedFields = new ArrayList<Field>();

   private ClassModel classModel;

   private int internalParallelClassModelsCount = 1;
   private final Map<Integer, ClassModel> internalParallelClassModels =
       new HashMap<Integer, ClassModel>();
   private final Map<String, ClassModel> parallelClassModelsByName =
       new HashMap<String, ClassModel>();
   private final Map<String, Integer> parallelClassNameToId =
       new HashMap<String, Integer>();

   private void addParallelClassModel(ClassModel model) {
       final String name = model.getMangledClassName();
       if (!parallelClassModelsByName.containsKey(name)) {
           final int id = internalParallelClassModelsCount++;
           internalParallelClassModels.put(id, model);
           parallelClassModelsByName.put(name, model);
           parallelClassNameToId.put(name, id);
       }
   }
   public Map<Integer, ClassModel> getInternalParallelClassModels() {
       return internalParallelClassModels;
   }
   public int getIdForParallelClassModel(ClassModel model) {
       String className = model.getMangledClassName();
       if (!parallelClassModelsByName.containsKey(className)) {
           throw new RuntimeException("Missing parallel class model " +
                   className);
       }
       return parallelClassNameToId.get(className);
   }
   public boolean isParallelClassModel(ClassModel model) {
       final String name = model.getMangledClassName();
       return parallelClassNameToId.containsKey(name);
   }

   public static final String clDevicePointerSize = "device.pointer_size";
   private final Map<String, String> config;
   public Map<String, String> getConfig() { return config; }

   private final HardCodedClassModels hardCodedClassModels;

   public HardCodedClassModels getHardCodedClassModels() {
       for (ClassModel model : allFieldsClasses) {
           if (model instanceof HardCodedClassModel) {
               hardCodedClassModels.addClassModelFor(model.getClassWeAreModelling(),
                       (HardCodedClassModel)model);
           }
       }
       return hardCodedClassModels;
   }

   private Object kernelInstance = null;

   private final boolean fallback = false;

   public static class DerivedFieldInfo {
       private String baseTypeHint;
       private String[] templateHint;
       private boolean isBroadcast;

       private static String removeSemicolon(String baseTypeHint) {
           final String converted;
           if (baseTypeHint != null && baseTypeHint.endsWith(";")) {
               converted = baseTypeHint.substring(0, baseTypeHint.length() - 1);
           } else {
               converted = baseTypeHint;
           }
           return converted;
       }

       public DerivedFieldInfo(String baseTypeHint, String[] templateHint,
               boolean isBroadcast) {
           this.baseTypeHint = removeSemicolon(baseTypeHint);
           this.templateHint = templateHint;
           this.isBroadcast = isBroadcast;
       }

       public String getHint() {
           // [Lscala/Tuple2<I,org/apache/spark/rdd/cl/tests/PointWithClassifier>
           if (baseTypeHint == null) {
               return null;
           } else {
               StringBuilder sb = new StringBuilder();
               sb.append(baseTypeHint);
               if (templateHint.length == 0) {
                   return sb.toString();
               } else {
                   sb.append("<");
                   for (int i = 0; i < templateHint.length; i++) {
                       if (templateHint[i] == null) {
                           throw new RuntimeException("Expected type template " +
                                   "hint " + i + " to be non-null");
                       }
                       if (i != 0) sb.append(",");
                       sb.append(templateHint[i]);
                   }
                   sb.append(">");
                   return sb.toString();
               }
           }
       }

       public boolean checkIsBroadcast() {
           return isBroadcast;
       }

       public void update(String newBaseTypeHint, String[] newTemplateHint,
               boolean newIsBroadcast) {
           this.baseTypeHint = removeSemicolon(newBaseTypeHint);
           assert isBroadcast == newIsBroadcast;
           assert templateHint.length == newTemplateHint.length;
           for (int i = 0; i < templateHint.length; i++) {
               if (templateHint[i] == null) {
                   if (newTemplateHint[i] != null) {
                       templateHint[i] = newTemplateHint[i];
                   }
               } else { // templateHint[i] != null
                   if (newTemplateHint[i] != null) {
                       assert templateHint[i].equals(newTemplateHint[i]);
                   }
               }
           }
       }
   }
   /*
    * A mapping from referenced fields to hints to their actual types. Some
    * fields may have their types obscured by Scala bytecode obfuscation (e.g.
    * scala.runtime.ObjectRef) but we can take guesses at the actual type based
    * on the references to them (e.g. if they are cast to something).
    */
   private final Map<String, DerivedFieldInfo> referencedFieldNames =
       new HashMap<String, DerivedFieldInfo>();

   private void addToReferencedFieldNames(String name, String baseTypeHint,
           String[] templateHint, boolean isBroadcast) {
     if (referencedFieldNames.containsKey(name)) {
       referencedFieldNames.get(name).update(baseTypeHint, templateHint,
               isBroadcast);
     } else {
       referencedFieldNames.put(name, new DerivedFieldInfo(baseTypeHint,
                   templateHint, isBroadcast));
     }
   }

   private void addToReferencedFieldNames(String name, String baseTypeHint,
           String[] templateHint) {
       addToReferencedFieldNames(name, baseTypeHint, templateHint, false);
   }

   private final Set<String> arrayFieldAssignments = new LinkedHashSet<String>();

   private final Set<String> arrayFieldAccesses = new LinkedHashSet<String>();

   // Classes of object array members
   private final StringToModel objectArrayFieldsClasses = new StringToModel();

   private final List<String> lexicalOrdering = new LinkedList<String>();

   private void addToObjectArrayFieldsClasses(String name, ClassModel model) {
       objectArrayFieldsClasses.add(name, model);
   }

   public ClassModel getModelFromObjectArrayFieldsClasses(String name,
          ClassModelMatcher matcher) {
      return objectArrayFieldsClasses.get(name, matcher);
   }

   public List<ClassModel> getModelsForClassName(String name) {
       return objectArrayFieldsClasses.getModels(name);
   }

   public Iterator<ClassModel> getObjectArrayFieldsClassesIterator() {
       return objectArrayFieldsClasses.iterator();
   }

   private ClassModel addClass(String name, String[] desc) throws AparapiException {
     final ClassModel model = getOrUpdateAllClassAccesses(name,
         new DescMatcher(desc));
     addToObjectArrayFieldsClasses(name, model);

     lexicalOrdering.add(name);
     allFieldsClasses.add(name, model);
     return model;
   }

   // Supporting classes of object array members like supers
   private final StringToModel allFieldsClasses = new StringToModel();

   // Keep track of arrays whose length is taken via foo.length
   private final Set<String> arrayFieldArrayLengthUsed = new LinkedHashSet<String>();

   private final List<MethodModel> calledMethods = new ArrayList<MethodModel>();

   private final MethodModel methodModel;

   /**
      True is an indication to use the fp64 pragma
   */
   private boolean usesDoubles;

   private boolean usesNew;

   /**
      True is an indication to use the byte addressable store pragma
   */
   private boolean usesByteWrites;

   /**
      True is an indication to use the atomics pragmas
   */
   private boolean usesAtomic32;

   private boolean usesAtomic64;

   public boolean requiresDoublePragma() {
      return usesDoubles;
   }

   public boolean requiresHeap() {
     return usesNew;
   }

   public boolean requiresByteAddressableStorePragma() {
      return usesByteWrites;
   }

   /* Atomics are detected in Entrypoint */
   public void setRequiresAtomics32Pragma(boolean newVal) {
      usesAtomic32 = newVal;
   }

   public void setRequiresAtomics64Pragma(boolean newVal) {
      usesAtomic64 = newVal;
   }

   public boolean requiresAtomic32Pragma() {
      return usesAtomic32;
   }

   public boolean requiresAtomic64Pragma() {
      return usesAtomic64;
   }

   public Object getKernelInstance() {
      return kernelInstance;
   }

   public void setKernelInstance(Object _k) {
      kernelInstance = _k;
   }

   public List<String> getLexicalOrderingOfObjectClasses() {
       return lexicalOrdering;
   }

   public static Field getFieldFromClassHierarchy(Class<?> _clazz, String _name) throws AparapiException {

      // look in self
      // if found, done

      // get superclass of curr class
      // while not found
      //  get its fields
      //  if found
      //   if not private, done
      //  if private, failure
      //  if not found, get next superclass

      Field field = null;

      assert _name != null : "_name should not be null";

      if (logger.isLoggable(Level.FINE)) {
         logger.fine("looking for " + _name + " in " + _clazz.getName());
      }

      try {
         field = _clazz.getDeclaredField(_name);
         final Class<?> type = field.getType();
         if (field.getAnnotation(Kernel.NoCL.class) != null) {
            return null;
         }
         return field;
      } catch (final NoSuchFieldException nsfe) {
         // This should be looger fine...
         //System.out.println("no " + _name + " in " + _clazz.getName());
      }

      Class<?> mySuper = _clazz.getSuperclass();

      if (logger.isLoggable(Level.FINE)) {
         logger.fine("looking for " + _name + " in " + mySuper.getName());
      }

      // Find better way to do this check
      while (mySuper != null && !mySuper.getName().equals(Kernel.class.getName())) {
         try {
            field = mySuper.getDeclaredField(_name);
            final int modifiers = field.getModifiers();
            if ((Modifier.isStatic(modifiers) == false) && (Modifier.isPrivate(modifiers) == false)) {
               final Class<?> type = field.getType();
               if (logger.isLoggable(Level.FINE)) {
                  logger.fine("field type is " + type.getName());
               }
               if (type.isPrimitive() || type.isArray()) {
                  return field;
               }
               throw new ClassParseException(ClassParseException.TYPE.OBJECTFIELDREFERENCE);
            } else {
               // This should be looger fine...
               //System.out.println("field " + _name + " not suitable: " + java.lang.reflect.Modifier.toString(modifiers));
               return null;
            }
         } catch (final NoSuchFieldException nsfe) {
            if (logger.isLoggable(Level.FINE)) {
               logger.fine("no " + _name + " in " + mySuper.getName());
            }
            mySuper = mySuper.getSuperclass();
         }
      }
      return null;
   }

   /*
    * Update the list of object array member classes and all the superclasses
    * of those classes and the fields in each class
    * 
    * It is important to have only one ClassModel for each class used in the kernel
    * and only one MethodModel per method, so comparison operations work properly.
    */
   public ClassModel getOrUpdateAllClassAccesses(String className,
          HardCodedClassModelMatcher matcher) throws AparapiException {
      ClassModel memberClassModel = allFieldsClasses.get(className,
              ClassModel.wrap(className, matcher));
      if (memberClassModel == null) {
         try {
            final Class<?> memberClass;
            if (className.startsWith("scala.Tuple2")) {
                memberClass = Class.forName("scala.Tuple2");
            } else {
                memberClass = Class.forName(className);
            }

            if (className.equals("org.apache.spark.mllib.linalg.DenseVector")) {
                memberClassModel = DenseVectorClassModel.create();
            } else if (className.equals("org.apache.spark.mllib.linalg.SparseVector")) {
                memberClassModel = SparseVectorClassModel.create();
            } else if (className.equals("scala.Array")) {
                // TODO
                memberClassModel = ScalaArrayClassModel.create(((DescMatcher)matcher).desc[0]);
            } else if (className.startsWith("scala.Tuple2") && !className.equals("scala.Tuple2")) {
                final String[] typesArr = parseTypeParameters(className);
                if (typesArr.length != 2) {
                    throw new RuntimeException("Expected two component type " +
                        "array, but had " + typesArr.length + " components");
                }
                memberClassModel = Tuple2ClassModel.create(typesArr[0], typesArr[1], true);
            } else {
                // Immediately add this class and all its supers if necessary
                memberClassModel = ClassModel.createClassModel(memberClass, this,
                    matcher);
            }

            if (logger.isLoggable(Level.FINEST)) {
               logger.finest("adding class " + className);
            }
            allFieldsClasses.add(className, memberClassModel);
            ClassModel superModel = memberClassModel.getSuperClazz();
            while (superModel != null) {
               // See if super is already added
               final ClassModel oldSuper = allFieldsClasses.get(
                   superModel.getClassWeAreModelling().getName(),
                   new NameMatcher(superModel.getClassWeAreModelling().getName()));
               if (oldSuper != null) {
                  if (oldSuper != superModel) {
                     memberClassModel.replaceSuperClazz(oldSuper);
                     if (logger.isLoggable(Level.FINEST)) {
                        logger.finest("replaced super " + oldSuper.getClassWeAreModelling().getName() + " for " + className);
                     }
                  }
               } else {
                  allFieldsClasses.add(superModel.getClassWeAreModelling().getName(), superModel);
                  if (logger.isLoggable(Level.FINEST)) {
                     logger.finest("add new super " + superModel.getClassWeAreModelling().getName() + " for " + className);
                  }
               }

               superModel = superModel.getSuperClazz();
            }
         } catch (final Exception e) {
            if (logger.isLoggable(Level.INFO)) {
               logger.info("Cannot find: " + className);
            }
            throw new AparapiException(e);
         }
      }

      return memberClassModel;
   }

   public ClassModelMethod resolveAccessorCandidate(
           final Instruction callInstance, final MethodEntryInfo _methodEntry)
           throws AparapiException {
      final String methodsActualClassName = _methodEntry.getClassName().replace('/', '.');

      if (callInstance != null &&
              !methodsActualClassName.startsWith("java.lang.") &&
              !methodsActualClassName.equals("scala.Tuple2") &&
              !methodsActualClassName.startsWith("scala.math") &&
              (!methodsActualClassName.startsWith("org.apache.spark") || methodsActualClassName.startsWith("org.apache.spark.rdd.cl"))) {
         // if (callInstance instanceof AccessArrayElement) {

            // It is a call from a member obj array element
            if (logger.isLoggable(Level.FINE)) {
               logger.fine("Looking for class in accessor call: " +
                       methodsActualClassName);
            }

            final String methodName = _methodEntry.getMethodName();
            final String methodDesc = _methodEntry.getMethodSig();
            final String returnType = methodDesc.substring(methodDesc.lastIndexOf(')') + 1);
            HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
                @Override
                public void checkPreconditions(List<HardCodedClassModel> classModels) {
                }

                @Override
                public boolean matches(HardCodedClassModel model) {
                    // TODO use _methodCall and _methodEntry?
                    String modelClassName = model.getClassWeAreModelling().getName();
                    if (modelClassName.equals(
                                KernelWriter.DENSEVECTOR_CLASSNAME)) {
                      return true;
                    } else if (modelClassName.equals(
                                KernelWriter.SPARSEVECTOR_CLASSNAME)) {
                      return true;
                    } else if (modelClassName.equals(
                                KernelWriter.TUPLE2_CLASSNAME)) {
                      TypeParameters params = model.getTypeParamDescs();
                      if (methodName.startsWith("_1")) {
                        String first = params.get(0);
                        if (returnType.length() == 1) {
                          // Primitive
                          return returnType.equals(first);
                        } else if (returnType.startsWith("L")) {
                          // Object
                          return first.startsWith("L"); // #*&$% type erasure
                        } else {
                          throw new RuntimeException(returnType);
                        }
                      } else if (methodName.startsWith("_2")) {
                        String second = params.get(1);
                        if (returnType.length() == 1) {
                          // Primitive
                          return returnType.equals(second);
                        } else if (returnType.startsWith("L")) {
                          // Object
                          return second.startsWith("L"); // #*&$% type erasure
                        } else {
                          throw new RuntimeException(returnType);
                        }
                      }
                    }
                    return false;
                }
            };

            final ClassModel memberClassModel = getOrUpdateAllClassAccesses(
                    methodsActualClassName, matcher);

            // false = no invokespecial allowed here
            return memberClassModel.getMethod(_methodEntry, false);
         // }
      }
      return null;
   }

   /*
    * Update accessor structures when there is a direct access to an 
    * obect array element's data members
    */
   public void updateObjectMemberFieldAccesses(final String className,
          final FieldEntry field) throws AparapiException {
      final String accessedFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();

      if (accessedFieldName.equals("MODULE$")) {
        return;
      }

      // Quickly bail if it is a ref
      if (field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8().startsWith("L")
            || field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8().startsWith("[L")) {
         System.err.println("Referencing field " + accessedFieldName + " in " + className);
         throw new ClassParseException(ClassParseException.TYPE.OBJECTARRAYFIELDREFERENCE);
      }

      if (logger.isLoggable(Level.FINEST)) {
         logger.finest("Updating access: " + className + " field:" + accessedFieldName);
      }

      HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher () {
          @Override
          public void checkPreconditions(List<HardCodedClassModel> classModels) { }

          @Override
          public boolean matches(HardCodedClassModel model) {
              /*
               * TODO can we use the type of field to infer the right Tuple2?
               * Maybe we need to have per-type HardCodedClassModel matches?
               */
              
              throw new UnsupportedOperationException();
          }
      };

      final ClassModel memberClassModel = getOrUpdateAllClassAccesses(className, matcher);
      final Class<?> memberClass = memberClassModel.getClassWeAreModelling();
      ClassModel superCandidate = null;

      // We may add this field if no superclass match
      boolean add = true;

      // No exact match, look for a superclass
      for (final ClassModel c : allFieldsClasses) {
         if (logger.isLoggable(Level.FINEST)) {
            logger.finest(" super: " + c.getClassWeAreModelling().getName() + " for " + className);
         }
         if (c.isSuperClass(memberClass)) {
            if (logger.isLoggable(Level.FINE)) {
               logger.fine("selected super: " + c.getClassWeAreModelling().getName() + " for " + className);
            }
            superCandidate = c;
            break;
         }

         if (logger.isLoggable(Level.FINEST)) {
            logger.finest(" no super match for " + memberClass.getName());
         }
      }

      // Look at super's fields for a match
      if (superCandidate != null) {
         final ArrayList<FieldNameInfo> structMemberSet = superCandidate.getStructMembers();
         for (final FieldNameInfo f : structMemberSet) {
            if (f.name.equals(accessedFieldName) &&
                   f.desc.equals(field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8())) {

               if (logger.isLoggable(Level.FINE)) {
                  logger.fine("Found match: " + accessedFieldName + " class: " + field.getClassEntry().getNameUTF8Entry().getUTF8()
                        + " to class: " + f.className);
               }

               if (!f.className.equals(field.getClassEntry().getNameUTF8Entry().getUTF8())) {
                  // Look up in class hierarchy to ensure it is the same field
                  final Field superField = getFieldFromClassHierarchy(superCandidate.getClassWeAreModelling(), f.name);
                  final Field classField = getFieldFromClassHierarchy(memberClass, f.name);
                  if (!superField.equals(classField)) {
                     throw new ClassParseException(ClassParseException.TYPE.OVERRIDENFIELD);
                  }
               }

               add = false;
               break;
            }
         }
      }

      // There was no matching field in the supers, add it to the memberClassModel
      // if not already there
      if (add) {
         boolean found = false;
         final ArrayList<FieldNameInfo> structMemberSet = memberClassModel.getStructMembers();
         for (final FieldNameInfo f : structMemberSet) {
            if (f.name.equals(accessedFieldName) &&
                  f.desc.equals(field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8())) {
               found = true;
            }
         }
         if (!found) {
            FieldNameInfo fieldInfo = new FieldNameInfo(field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8(),
                field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8(),
                field.getClassEntry().getNameUTF8Entry().getUTF8());
            structMemberSet.add(fieldInfo);
            if (logger.isLoggable(Level.FINE)) {
               logger.fine("Adding assigned field " + field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8() + " type: "
                     + field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8() + " to "
                     + memberClassModel.getClassWeAreModelling().getName());
            }
         }
      }
   }

   private static Instruction getCallInstance(final MethodCall methodCall) {
      if (methodCall instanceof VirtualMethodCall) {
          return ((VirtualMethodCall) methodCall).getInstanceReference();
      } else {
          return null;
      }
   }

   /*
    * Find a suitable call target in the kernel class, supers, object members or static calls
    */
   ClassModelMethod resolveCalledMethod(final MethodEntryInfo methodEntry,
           final boolean isSpecial, final boolean isStatic,
           ClassModel classModel, Instruction callInstance) throws AparapiException {

      int thisClassIndex = classModel.getThisClassConstantPoolIndex();//arf
      boolean isMapped = (thisClassIndex != methodEntry.getClassIndex()) &&
          Kernel.isMappedMethod(methodEntry);
      if (logger.isLoggable(Level.FINE)) {
         if (isSpecial) {
            logger.fine("Method call to super: " + methodEntry);
         } else if (thisClassIndex != methodEntry.getClassIndex()) {
            logger.fine("Method call to ??: " + methodEntry + ", isMappedMethod=" + isMapped);
         } else {
            logger.fine("Method call in kernel class: " + methodEntry);
         }
      }

      ClassModelMethod m = classModel.getMethod(methodEntry, isSpecial);

      // Did not find method in this class or supers. Look for data member object arrays
      if (m == null && !isMapped) {
         m = resolveAccessorCandidate(callInstance, methodEntry);
      }

      // Look for a intra-object call in a object member
      if (m == null && !isMapped) {
         String targetMethodOwner = methodEntry.getClassName().replace('/', '.');
         final Set<ClassModel> possibleMatches = new HashSet<ClassModel>();

         for (ClassModel c : allFieldsClasses) {
            if (c.getClassWeAreModelling().getName().equals(targetMethodOwner)) {
               m = c.getMethod(methodEntry, isSpecial);
            } else if (c.classNameMatches(targetMethodOwner)) {
               possibleMatches.add(c);
            }
         }

         if (m == null) {
             for (ClassModel c : possibleMatches) {
                 m = c.getMethod(methodEntry, isSpecial);
                 if (m != null) break;
             }
         }
      }

      Set<String> ignorableClasses = new HashSet<String>();
      ignorableClasses.add("scala/runtime/BoxesRunTime");
      ignorableClasses.add("scala/runtime/IntRef");
      ignorableClasses.add("scala/runtime/FloatRef");
      ignorableClasses.add("scala/runtime/DoubleRef");
      // Look for static call to some other class
      if ((m == null) && !isMapped && isStatic &&
          !ignorableClasses.contains(methodEntry.getClassName())) {

         String otherClassName = methodEntry.getClassName().replace('/', '.');
         HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
             @Override
             public void checkPreconditions(List<HardCodedClassModel> classModels) { }

             @Override
             public boolean matches(HardCodedClassModel model) {
                 // TODO use _methodEntry?
                 throw new UnsupportedOperationException();
             }
         };
         ClassModel otherClassModel = getOrUpdateAllClassAccesses(otherClassName, matcher);

         m = otherClassModel.getMethod(methodEntry, false);
      }

      if (logger.isLoggable(Level.INFO)) {
         logger.fine("Selected method for: " + methodEntry + " is " + m);
      }

      return m;
   }

   public static boolean isSparseVectorIndicesOrValues(Instruction insn) {
       if (insn instanceof I_INVOKEVIRTUAL) {
           final MethodEntryInfo methodEntry = ((I_INVOKEVIRTUAL)insn)
               .getConstantPoolMethodEntry();
           final String methodName = methodEntry.getMethodName();
           final String methodDesc = methodEntry.getMethodSig();
           final String owner = methodEntry.getClassName();
           if (owner.equals("org/apache/spark/mllib/linalg/SparseVector")) {
               if ((methodName.equals("indices") && methodDesc.equals("()[I")) ||
                       (methodName.equals("values") && methodDesc.equals("()[D"))) {
                   return true;
               }
           }
       }
       return false;
   }

   private static String getMethodEntryName(MethodEntry methodEntry) {
       return methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
   }

   private static String getMethodEntryClass(MethodEntry methodEntry) {
       return methodEntry.getClassEntry().getNameUTF8Entry().getUTF8();
   }

   private static String getMethodEntryDesc(MethodEntry methodEntry) {
       return methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
   }

   public Entrypoint(ClassModel _classModel, MethodModel _methodModel,
           Object _k, Collection<ScalaArrayParameter> params,
           HardCodedClassModels setHardCodedClassModels,
           Map<String, String> config) throws AparapiException {
      this.config = config;
      classModel = _classModel;
      methodModel = _methodModel;
      kernelInstance = _k;
      if (setHardCodedClassModels == null) {
          hardCodedClassModels = new HardCodedClassModels();
      } else {
          hardCodedClassModels = setHardCodedClassModels;
      }
      
      // Add all hard coded class models to the set of class models
      for (HardCodedClassModel model : hardCodedClassModels) {
          for (String desc : model.getNestedTypeDescs()) {
              // Convert object desc to class name
              if (desc.startsWith("L") || desc.startsWith("[")) {
                  String nestedClass = desc.substring(1, desc.length() - 1);
                  lexicalOrdering.add(nestedClass);
                  addToObjectArrayFieldsClasses(nestedClass,
                      getOrUpdateAllClassAccesses(nestedClass,
                        new ShouldNotCallMatcher()));
              }
          }

          lexicalOrdering.add(model.getClassWeAreModelling().getName());
          addToObjectArrayFieldsClasses(model.getClassWeAreModelling().getName(), model);
      }

      // If we're working on a nested class (e.g. a Scala lambda), add its parent
      Class<?> enclosingClass = _classModel.getClassWeAreModelling().getEnclosingClass();
      while (enclosingClass != null) {
          addClass(enclosingClass.getName(), new String[0]);
          enclosingClass = enclosingClass.getEnclosingClass();
      }

      if (params != null) {
        for (ScalaArrayParameter p : params) {
          if (p.getClazz() != null) {
            addClass(p.getClazz().getName(), p.getDescArray());
          }
        }
      }

      final Map<ClassModelMethod, MethodModel> methodMap =
          new LinkedHashMap<ClassModelMethod, MethodModel>();

      boolean discovered = true;

      // Record which pragmas we need to enable
      if (methodModel.requiresDoublePragma()) {
         usesDoubles = true;
         if (logger.isLoggable(Level.FINE)) {
            logger.fine("Enabling doubles on " + methodModel.getName());
         }
      }
      if (methodModel.requiresHeap()) {
        usesNew = true;
      }
      if (methodModel.requiresByteAddressableStorePragma()) {
         usesByteWrites = true;
         if (logger.isLoggable(Level.FINE)) {
            logger.fine("Enabling byte addressable on " + methodModel.getName());
         }
      }

      /*
       * Collect all methods called directly from kernel's run method. Store a
       * mapping from each ClassModelMethod to the MethodModel object for each
       * called method, and add the MethodModel to the list of called methods
       * for the run method.
       */
      for (final MethodCall methodCall : methodModel.getMethodCalls()) {
         final MethodEntryInfo methodEntry = methodCall.getConstantPoolMethodEntry();

         final ClassModelMethod m = resolveCalledMethod(
                 methodCall.getConstantPoolMethodEntry(),
                 methodCall instanceof I_INVOKESPECIAL,
                 methodCall instanceof I_INVOKESTATIC, classModel,
                 getCallInstance(methodCall));
         if ((m != null) && !methodMap.keySet().contains(m) && !noCL(m)) {
             final MethodModel target = new LoadedMethodModel(m, this);
             methodMap.put(m, target);
             methodModel.getCalledMethods().add(target);
             discovered = true;
         }
      }

      // methodMap now contains a list of method called by run itself().
      // Walk the whole graph of called methods and add them to the methodMap
      while (!fallback && discovered) {
         discovered = false;
         for (final MethodModel mm : new ArrayList<MethodModel>(methodMap.values())) {
            for (final MethodCall methodCall : mm.getMethodCalls()) {
               final MethodEntryInfo methodEntry = methodCall.getConstantPoolMethodEntry();
               final String methodName = methodEntry.getMethodName();
               final String methodClass = methodEntry.getClassName();

               final ClassModelMethod m = resolveCalledMethod(methodEntry,
                       methodCall instanceof I_INVOKESPECIAL,
                       methodCall instanceof I_INVOKESTATIC, classModel,
                       getCallInstance(methodCall));

               if (m != null && !noCL(m)) {
                  MethodModel target = null;
                  if (methodMap.keySet().contains(m)) {
                     // we remove and then add again.  Because this is a LinkedHashMap this 
                     // places this at the end of the list underlying the map
                     // then when we reverse the collection (below) we get the method 
                     // declarations in the correct order.  We are trying to
                     // avoid creating forward references
                     target = methodMap.remove(m);
                     if (logger.isLoggable(Level.FINEST)) {
                        logger.fine("repositioning : " +
                                m.getClassModel().getClassWeAreModelling()
                                .getName() + " " + m.getName() + " " +
                                m.getDescriptor());
                     }
                  } else {
                     target = new LoadedMethodModel(m, this);
                     discovered = true;
                  }
                  methodMap.put(m, target);
                  // Build graph of call targets to look for recursion
                  mm.getCalledMethods().add(target);
               }
            }
         }
      }

      methodModel.checkForRecursion(new HashSet<MethodModel>());

      if (logger.isLoggable(Level.FINE)) {
         logger.fine("fallback=" + fallback);
      }

      if (!fallback) {
         calledMethods.addAll(methodMap.values());
         Collections.reverse(calledMethods);
         final List<MethodModel> methods = new ArrayList<MethodModel>(calledMethods);

         // add method to the calledMethods so we can include in this list
         methods.add(methodModel);
         final Set<String> fieldAssignments = new HashSet<String>();

         final Set<String> fieldAccesses = new HashSet<String>();

         // This is just a prepass that collects metadata, we don't actually write kernels at this point
         for (final MethodModel methodModel : methods) {

            // Record which pragmas we need to enable
            if (methodModel.requiresDoublePragma()) {
               usesDoubles = true;
               if (logger.isLoggable(Level.FINE)) {
                  logger.fine("Enabling doubles on " + methodModel.getName());
               }
            }
            if (methodModel.requiresHeap()) {
              usesNew = true;
            }
            if (methodModel.requiresByteAddressableStorePragma()) {
               usesByteWrites = true;
               if (logger.isLoggable(Level.FINE)) {
                  logger.fine("Enabling byte addressable on " + methodModel.getName());
               }
            }

            for (Instruction instruction = methodModel.getPCHead(); instruction != null; instruction =
                instruction.getNextPC()) {

               if (instruction instanceof AssignToLocalVariable) {
                   int countChildren = 0;
                   for (Instruction operand = instruction.getFirstChild();
                           operand != null; operand = operand.getNextExpr()) {
                       countChildren ++;
                   }
                   if (countChildren == 1) {
                       Instruction child = instruction.getFirstChild();
                       if (isSparseVectorIndicesOrValues(child)) {
                           throw new RuntimeException("Assigning from the indices " +
                                   "or values of a MLLib Sparse Vector is not supported");
                       }
                   }
               }

               if (instruction instanceof AssignToArrayElement) {
                  final AssignToArrayElement assignment = (AssignToArrayElement) instruction;

                  final Instruction arrayRef = assignment.getArrayRef();
                  // AccessField here allows instance and static array refs
                  if (arrayRef instanceof I_GETFIELD) {
                     final I_GETFIELD getField = (I_GETFIELD) arrayRef;
                     final FieldEntry field = getField.getConstantPoolFieldEntry();
                     final String assignedArrayFieldName = field
                         .getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
                     arrayFieldAssignments.add(assignedArrayFieldName);
                     addToReferencedFieldNames(assignedArrayFieldName, null,
                             EMPTY_STRING_ARRAY);
                     arrayFieldArrayLengthUsed.add(assignedArrayFieldName);

                  }
               } else if (instruction instanceof AccessArrayElement) {
                  final AccessArrayElement access = (AccessArrayElement) instruction;

                  final Instruction arrayRef = access.getArrayRef();
                  // AccessField here allows instance and static array refs
                  if (arrayRef instanceof I_GETFIELD) {
                     final I_GETFIELD getField = (I_GETFIELD) arrayRef;
                     final FieldEntry field = getField.getConstantPoolFieldEntry();
                     final String accessedArrayFieldName = field
                         .getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
                     arrayFieldAccesses.add(accessedArrayFieldName);
                     addToReferencedFieldNames(accessedArrayFieldName, null,
                             EMPTY_STRING_ARRAY);
                     arrayFieldArrayLengthUsed.add(accessedArrayFieldName);

                  }
               } else if (instruction instanceof I_ARRAYLENGTH) {
                  Instruction child = instruction.getFirstChild();
                  while (child instanceof I_AALOAD) {
                     child = child.getFirstChild();
                  }
                  // if (!(child instanceof AccessField)) {
                  //    throw new ClassParseException(
                  //            ClassParseException.TYPE.LOCALARRAYLENGTHACCESS);
                  // }
                  if (child instanceof AccessField) {
                     final AccessField childField = (AccessField) child;
                     final String arrayName = childField
                         .getConstantPoolFieldEntry().getNameAndTypeEntry()
                         .getNameUTF8Entry().getUTF8();
                     arrayFieldArrayLengthUsed.add(arrayName);
                     if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Noted arraylength in " +
                                methodModel.getName() + " on " + arrayName);
                     }
                  } else if (child instanceof LocalVariableConstIndexLoad) {
                     LocalVariableConstIndexLoad accessLocal = (LocalVariableConstIndexLoad)child;
                     final String arrayName = accessLocal.getLocalVariableInfo()
                         .getVariableName();
                  } else {
                     throw new ClassParseException(
                             ClassParseException.TYPE.LOCALARRAYLENGTHACCESS);
                  }
               } else if (instruction instanceof AccessField) {
                  final AccessField access = (AccessField) instruction;
                  final FieldEntry field = access.getConstantPoolFieldEntry();
                  final String accessedFieldName = field.getNameAndTypeEntry()
                      .getNameUTF8Entry().getUTF8();
                  fieldAccesses.add(accessedFieldName);
                  final String signature;
                  if (access instanceof ScalaGetObjectRefField) {
                    ScalaGetObjectRefField scalaGet = (ScalaGetObjectRefField)access;
                    I_CHECKCAST cast = scalaGet.getCast();
                    signature = cast.getConstantPoolClassEntry()
                        .getNameUTF8Entry().getUTF8().replace('.', '/');
                    if (signature.startsWith("[Lscala/Tuple2")) {
                        String[] newArr = new String[2];
                        addToReferencedFieldNames(accessedFieldName, signature, newArr);
                    } else {
                        addToReferencedFieldNames(accessedFieldName, signature,
                                EMPTY_STRING_ARRAY);
                    }
                  } else {
                    signature = field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
                    if (signature.equals("Lorg/apache/spark/broadcast/Broadcast;")) {
                        Instruction next = instruction.getNextPC();
                        if (!(next instanceof I_INVOKEVIRTUAL)) {
                            throw new RuntimeException("Expected I_INVOKEVIRTUAL, got " + next);
                        }
                        Instruction next_next = next.getNextPC();
                        if (!(next_next instanceof I_CHECKCAST)) {
                            throw new RuntimeException("Expected I_CHECKCAST, got " + next_next);
                        }
                        I_CHECKCAST cast = (I_CHECKCAST)next_next;
                        String typeName = cast.getConstantPoolClassEntry()
                            .getNameUTF8Entry().getUTF8();
                        if (!typeName.startsWith("[")) {
                            throw new RuntimeException("Broadcast variables " +
                                    "should always have an array type");
                        }

                        String[] templateHint = EMPTY_STRING_ARRAY;
                        if (typeName.equals("[Lscala/Tuple2;")) {
                            templateHint = new String[2];
                            /*
                             * Look for an expression being applied to this
                             * broadcast variable (which must be an array of
                             * tuples) that loads an element from the array and
                             * then calls a virtual method on it (hopefully
                             * referencing one of its fields, from which we can
                             * get the type).
                             */
                            if (cast.getParentExpr() instanceof I_AALOAD &&
                                    cast.getParentExpr().getParentExpr()
                                    instanceof I_INVOKEVIRTUAL) {
                                I_INVOKEVIRTUAL accessor = (I_INVOKEVIRTUAL)cast
                                    .getParentExpr().getParentExpr();
                                final MethodEntryInfo methodEntry = accessor
                                    .getConstantPoolMethodEntry();
                                final String methodName = methodEntry.getMethodName();
                                final String methodDesc = methodEntry.getMethodSig();
                                if (!methodName.startsWith("_1") && !methodName.startsWith("_2")) {
                                    throw new RuntimeException("Expected " +
                                            "method name starting with _1 or " +
                                            "_2 but found " + methodName);
                                }

                                final String realType;
                                if (accessor.getParentExpr() instanceof I_CHECKCAST) {
                                    I_CHECKCAST cast_to_real = (I_CHECKCAST)accessor.getParentExpr();
                                    realType = cast_to_real
                                        .getConstantPoolClassEntry()
                                        .getNameUTF8Entry().getUTF8();
                                } else {
                                    realType = methodDesc.substring(methodDesc.lastIndexOf(')') + 1);
                                }

                                if (methodName.startsWith("_1")) {
                                    templateHint[0] = realType;
                                } else if (methodName.startsWith("_2")) {
                                    templateHint[1] = realType;
                                }
                                arrayFieldArrayLengthUsed.add(accessedFieldName);
                            }
                        }
                        addToReferencedFieldNames(accessedFieldName, typeName,
                                templateHint, true);
                        if (typeName.substring(1).startsWith("L")) {
                            // Broadcast variable is an array of objects
                            final String className = (typeName.substring(2,
                                        typeName.length() - 1)).replace('/', '.');
                            lexicalOrdering.add(className);

                            HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
                                @Override
                                public void checkPreconditions(List<HardCodedClassModel> classModels) { }

                                @Override
                                    public boolean matches(HardCodedClassModel model) {
                                        return className.equals(model.getClassWeAreModelling().getName());
                                    }
                            };
                            final ClassModel arrayFieldModel =
                                getOrUpdateAllClassAccesses(className, matcher);
                            addToObjectArrayFieldsClasses(className, arrayFieldModel);
                        }
                    } else {
                        addToReferencedFieldNames(accessedFieldName, null,
                                EMPTY_STRING_ARRAY);
                    }
                  }

                  if (logger.isLoggable(Level.FINE)) {
                     logger.fine("AccessField field type= " + signature + " in " + methodModel.getName());
                  }

                  // Add the class model for the referenced obj array
                  if (signature.startsWith("[L")) {
                     // Turn [Lcom/amd/javalabs/opencl/demo/DummyOOA; into com.amd.javalabs.opencl.demo.DummyOOA for example
                     final String className = (signature.substring(2, signature.length() - 1)).replace('/', '.');
                     HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
                         @Override
                         public void checkPreconditions(List<HardCodedClassModel> classModels) { }

                         @Override
                         public boolean matches(HardCodedClassModel model) {
                             return className.equals(model.getClassWeAreModelling().getName());
                         }
                     };

                     final ClassModel arrayFieldModel = getOrUpdateAllClassAccesses(className, matcher);
                     if (arrayFieldModel != null) {
                        if (arrayFieldModel instanceof HardCodedClassModel) {
                          HardCodedClassModel arrayModel = (HardCodedClassModel)arrayFieldModel;
                          final String arrayClassName = arrayModel.getClassWeAreModelling().getName();
                          final String baseType = "[L" + arrayClassName.replace('.', '/');
                          final TypeParameters typeParams = arrayModel.getTypeParamDescs();
                          final String[] paramsArr = new String[typeParams.size()];
                          for (int i = 0; i < paramsArr.length; i++) {
                              paramsArr[i] = Tuple2ClassModel.descToName(
                                      typeParams.get(i)).replace('.', '/');
                          }
                          addToReferencedFieldNames(accessedFieldName, baseType, paramsArr);
                        }
                        final Class<?> memberClass = arrayFieldModel.getClassWeAreModelling();
                        final int modifiers = memberClass.getModifiers();
                        // if (!Modifier.isFinal(modifiers)) {
                        //    throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTNONFINAL);
                        // }

                        final ClassModel refModel = getModelFromObjectArrayFieldsClasses(className,
                            new ClassModelMatcher() {
                                @Override
                                public boolean matches(ClassModel model) {
                                    return className.equals(model.getClassWeAreModelling().getName());
                                }
                            });
                        if (refModel == null) {

                           // Verify no other member with common parent
                           for (final ClassModel memberObjClass : objectArrayFieldsClasses) {
                              ClassModel superModel = memberObjClass;
                              while (superModel != null) {
                                 if (superModel.isSuperClass(memberClass)) {
                                    throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTFIELDNAMECONFLICT);
                                 }
                                 superModel = superModel.getSuperClazz();
                              }
                           }

                           addToObjectArrayFieldsClasses(className, arrayFieldModel);
                           if (logger.isLoggable(Level.FINE)) {
                              logger.fine("adding class to objectArrayFields: " + className);
                           }
                        }
                     }
                     lexicalOrdering.add(className);
                  } else {
                     final String className = (field.getClassEntry().getNameUTF8Entry().getUTF8()).replace('/', '.');
                     final Set<String> ignoreScalaRuntimeStuff = new HashSet<String>();
                     ignoreScalaRuntimeStuff.add("scala.runtime.RichInt$");
                     ignoreScalaRuntimeStuff.add("scala.Predef$");
                     ignoreScalaRuntimeStuff.add("scala.ObjectRef");
                     ignoreScalaRuntimeStuff.add("scala.runtime.ObjectRef");
                     ignoreScalaRuntimeStuff.add("scala.runtime.IntRef");
                     ignoreScalaRuntimeStuff.add("scala.math.package$");
                     /*
                      * Ignore some internal scala stuff, because we won't be emitting any code based on them anyway.
                      * In general, this is a lot of Scala boxing/unboxing code that just translates down to a single
                      * int, double, float, etc.
                      */
                     if (!ignoreScalaRuntimeStuff.contains(className)) {
                       // Look for object data member access
                       if (!className.equals(getClassModel().getClassWeAreModelling().getName())
                             && (getFieldFromClassHierarchy(getClassModel()
                             .getClassWeAreModelling(), accessedFieldName) == null)) {
                          updateObjectMemberFieldAccesses(className, field);
                       }
                     }
                  }
               } else if (instruction instanceof AssignToField) {
                  final AssignToField assignment = (AssignToField) instruction;
                  final FieldEntry field = assignment.getConstantPoolFieldEntry();
                  final String assignedFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
                  fieldAssignments.add(assignedFieldName);
                  addToReferencedFieldNames(assignedFieldName, null, EMPTY_STRING_ARRAY);

                  final String className = (field.getClassEntry().getNameUTF8Entry().getUTF8()).replace('/', '.');
                  // Look for object data member access
                  if (!className.equals(getClassModel().getClassWeAreModelling().getName())
                        && (getFieldFromClassHierarchy(getClassModel().getClassWeAreModelling(), assignedFieldName) == null)) {
                     updateObjectMemberFieldAccesses(className, field);
                  } else {

                     if ((!Config.enablePUTFIELD) && methodModel.methodUsesPutfield() && !methodModel.isSetter()) {
                        throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTONLYSUPPORTSSIMPLEPUTFIELD);
                     }

                  }

               } else if (instruction instanceof I_INVOKEVIRTUAL) {
                  final I_INVOKEVIRTUAL invokeInstruction = (I_INVOKEVIRTUAL) instruction;
                  final MethodEntryInfo methodEntry = invokeInstruction
                      .getConstantPoolMethodEntry();
                  MethodModel invokedMethod = invokeInstruction.getMethod();
                  FieldEntry getterField = getSimpleGetterField(invokedMethod);
                  if (getterField != null) {
                     addToReferencedFieldNames(getterField.getNameAndTypeEntry()
                             .getNameUTF8Entry().getUTF8(), null, EMPTY_STRING_ARRAY);
                  } else {
                     if (Kernel.isMappedMethod(methodEntry)) { //only do this for intrinsics

                        if (Kernel.usesAtomic32(methodEntry)) {
                           setRequiresAtomics32Pragma(true);
                        }

                        final Arg methodArgs[] = methodEntry.getArgs();
                        if ((methodArgs.length > 0) && methodArgs[0].isArray()) {
                           final Instruction arrInstruction = invokeInstruction.getArg(0);
                           if (arrInstruction instanceof AccessField) {
                              final AccessField access = (AccessField) arrInstruction;
                              final FieldEntry field = access.getConstantPoolFieldEntry();
                              final String accessedFieldName = field
                                  .getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
                              arrayFieldAssignments.add(accessedFieldName);
                              addToReferencedFieldNames(accessedFieldName, null,
                                      EMPTY_STRING_ARRAY);
                           }
                           else {
                              throw new ClassParseException(
                                      ClassParseException.TYPE.ACCESSEDOBJECTSETTERARRAY);
                           }
                        }
                     }
                  }
               }
            }
         }

         for (final Map.Entry<String, DerivedFieldInfo> referencedField :
                 referencedFieldNames.entrySet()) {
            String referencedFieldName = referencedField.getKey();
            DerivedFieldInfo fieldInfo = referencedField.getValue(); // may be null
            String typeHint = fieldInfo.getHint();

            try {
               final Class<?> clazz = classModel.getClassWeAreModelling();
               final Field field = getFieldFromClassHierarchy(clazz, referencedFieldName);
               if (field != null) {
                  referencedFields.add(field);
                  final ClassModelField ff = classModel.getField(referencedFieldName);
                  if (ff == null) {
                     throw new RuntimeException("ff should not be null for " +
                             clazz.getName() + "." + referencedFieldName);
                  }
                  if (typeHint != null) ff.setTypeHint(typeHint);
                  referencedClassModelFields.add(ff);
                  isBroadcasted.add(fieldInfo.checkIsBroadcast());
               }
            } catch (final SecurityException e) {
               e.printStackTrace();
            }
         }

         // Build data needed for oop form transforms if necessary
         if (!objectArrayFieldsClasses.isEmpty()) {

            for (final ClassModel memberObjClass : objectArrayFieldsClasses) {
               // At this point we have already done the field override safety check, so 
               // add all the superclass fields into the kernel member class to be
               // sorted by size and emitted into the struct
               ClassModel superModel = memberObjClass.getSuperClazz();
               while (superModel != null) {
                  if (logger.isLoggable(Level.FINEST)) {
                     logger.finest("adding = " +
                             superModel.getClassWeAreModelling().getName() +
                             " fields into " +
                             memberObjClass.getClassWeAreModelling().getName());
                  }
                  memberObjClass.getStructMembers().addAll(superModel.getStructMembers());
                  superModel = superModel.getSuperClazz();
               }
            }

            // Sort fields of each class biggest->smallest
            final Comparator<FieldNameInfo> fieldSizeComparator = new Comparator<FieldNameInfo>(){
               @Override public int compare(FieldNameInfo aa, FieldNameInfo bb) {
                  final String aType = aa.desc;
                  final String bType = bb.desc;

                  // Booleans get converted down to bytes
                  final int aSize = getSizeOf(aType);
                  final int bSize = getSizeOf(bType);

                  if (logger.isLoggable(Level.FINEST)) {
                     logger.finest("aType= " + aType + " aSize= " + aSize +
                             " . . bType= " + bType + " bSize= " + bSize);
                  }

                  // Note this is sorting in reverse order so the biggest is first
                  if (aSize > bSize) {
                     return -1;
                  } else if (aSize == bSize) {
                     return 0;
                  } else {
                     return 1;
                  }
               }
            };

            for (final ClassModel c : objectArrayFieldsClasses) {
                final ArrayList<FieldNameInfo> fields = c.getStructMembers();
                if (fields.size() > 0) {
                    Collections.sort(fields, fieldSizeComparator);
                    // Now compute the total size for the struct
                    int totalSize = 0;

                    if (c instanceof HardCodedClassModel) {
                        totalSize = ((HardCodedClassModel)c).calcTotalStructSize(this);
                    } else {
                       for (final FieldNameInfo f : fields) {
                          // Record field offset for use while copying
                          // Get field we will copy out of the kernel member object
                          final Field rfield = getFieldFromClassHierarchy(
                                  c.getClassWeAreModelling(), f.name);

                          long fieldOffset = UnsafeWrapper.objectFieldOffset(rfield);
                          final String fieldType = f.desc;

                          c.addStructMemberInfo(fieldOffset,
                                  fieldType, f.name);

                          final int fSize = getSizeOf(fieldType);

                          totalSize += fSize;
                       }
                       c.generateStructMemberArray(this);
                    }

                    // compute total size for OpenCL buffer
                    int totalStructSize = totalSize;
                    c.setTotalStructSize(totalStructSize);
                }
            }

            for (HardCodedClassModel c : hardCodedClassModels) {
                final ArrayList<FieldNameInfo> fields = c.getStructMembers();
                if (fields.size() > 0) {
                    Collections.sort(fields, fieldSizeComparator);
                    // Now compute the total size for the struct
                    int totalSize = c.calcTotalStructSize(this);
                    c.setTotalStructSize(totalSize);
                }
            }
         }
      }
   }

   private final Map<String, Integer> sizeOfCache = new HashMap<String, Integer>();

   public static String[] parseTypeParameters(String fullDesc) {
       final int openBraceIndex = fullDesc.indexOf("<");
       final int closeBraceIndex = fullDesc.indexOf(">");
       if (openBraceIndex != -1 && closeBraceIndex != -1) {
           final String types = fullDesc.substring(openBraceIndex + 1, closeBraceIndex);
           final String[] typesArr = types.split(",");
           return typesArr;
       } else {
           // e.g. scala.Tuple2$mcID$sp
           final String tuple2Prefix = "scala.Tuple2$mc";
           if (fullDesc.startsWith(tuple2Prefix)) {
               String primitiveDescriptors = fullDesc.substring(tuple2Prefix.length());
               primitiveDescriptors = primitiveDescriptors.substring(0, 2);
               final String[] typesArr = new String[2];
               typesArr[0] = Character.toString(primitiveDescriptors.charAt(0));
               typesArr[1] = Character.toString(primitiveDescriptors.charAt(1));
               return typesArr;
           } else {
               throw new RuntimeException("Expected open and close brace in \"" + fullDesc + "\"");
           }
       }
   }

   private static boolean descAndModelMatches(String desc, ClassModel cm) {
       String classDesc = "L" + cm.getClassWeAreModelling().getName() + ";";
       if (desc.equals(classDesc)) return true;

       if (cm instanceof HardCodedClassModel && desc.indexOf("<") != -1) {
           // Some type parameters to help us guess
           final HardCodedClassModel hardCoded = (HardCodedClassModel)cm;
           final List<String> typeDescs = hardCoded.getNestedTypeDescs();
           final String[] typeParams = parseTypeParameters(desc);

           if (typeDescs.size() == typeParams.length) {
               boolean match = true;
               for (int i = 0; i < typeDescs.size() && match; i++) {
                   if (!typeDescs.get(i).equals(typeParams[i])) {
                       match = false;
                   }
               }
               return match;
           }
       }
       return false;
   }

   public int getSizeOf(String desc) {
       if (desc.equals("Z")) desc = "B";

       if (desc.startsWith("L")) {
           if (sizeOfCache.containsKey(desc)) {
               return sizeOfCache.get(desc);
           }

           for (final ClassModel cm : objectArrayFieldsClasses) {
             if (descAndModelMatches(desc, cm)) {
               final int size = cm.getTotalStructSize();
               if (size > 0) {
                   sizeOfCache.put(desc, cm.getTotalStructSize());
               }
               return size;
             }
           }
       }

       if (desc.startsWith("[")) {
           return Integer.parseInt(getConfig().get(
                       Entrypoint.clDevicePointerSize));
       }

       return InstructionSet.TypeSpec.valueOf(desc).getSize();
   }

   private boolean noCL(ClassModelMethod m) {
      boolean found = m.getClassModel().getNoCLMethods().contains(m.getName());
      return found;
   }

   private FieldEntry getSimpleGetterField(MethodModel method) {
      return method.getAccessorVariableFieldEntry();
   }

   public boolean shouldFallback() {
      return (fallback);
   }

   public List<ClassModel.ClassModelField> getReferencedClassModelFields() {
      return (referencedClassModelFields);
   }
   public boolean isBroadcastField(ClassModel.ClassModelField field) {
       assert referencedClassModelFields.size() == isBroadcasted.size();
       for (int i = 0; i < referencedClassModelFields.size(); i++) {
           if (referencedClassModelFields.get(i) == field) {
               return isBroadcasted.get(i);
           }
       }
       throw new RuntimeException();
   }

   public List<Field> getReferencedFields() {
      return (referencedFields);
   }

   public List<MethodModel> getCalledMethods() {
      return calledMethods;
   }

   public Map<String, DerivedFieldInfo> getReferencedFieldNames() {
      return (referencedFieldNames);
   }

   public Set<String> getArrayFieldAssignments() {
      return (arrayFieldAssignments);
   }

   public Set<String> getArrayFieldAccesses() {
      return (arrayFieldAccesses);
   }

   public Set<String> getArrayFieldArrayLengthUsed() {
      return (arrayFieldArrayLengthUsed);
   }

   public MethodModel getMethodModel() {
      return (methodModel);
   }

   public ClassModel getClassModel() {
      return (classModel);
   }

   private MethodModel lookForHardCodedMethod(MethodEntryInfo _methodEntry,
           ClassModel classModel, String guess) {
       try {
           MethodModel hardCoded = classModel.checkForHardCodedMethods(
                   _methodEntry.getMethodName(), _methodEntry.getMethodSig(), guess);
           if (hardCoded != null) {
               return hardCoded;
           }
       } catch (AparapiException a) {
           throw new RuntimeException(a);
       }
       return null;
   }

   /*
    * Return the best call target MethodModel by looking in the class hierarchy
    * @param _methodEntry MethodEntry for the desired target
    * @return the fully qualified name such as "com_amd_javalabs_opencl_demo_PaternityTest$SimpleKernel__actuallyDoIt"
    */
   public MethodModel getCallTarget(MethodEntryInfo _methodEntry,
           boolean _isSpecial, String guess) {

      final String methodName = _methodEntry.getMethodName();
      ClassModelMethod target = getClassModel().getMethod(_methodEntry, _isSpecial);
      boolean isMapped = Kernel.isMappedMethod(_methodEntry);

      if (logger.isLoggable(Level.FINE) && (target == null)) {
         logger.fine("Did not find call target: " + _methodEntry.toString() + " in " +
             getClassModel().getClassWeAreModelling().getName() + " isMapped=" +
             isMapped);
      }

      String entryClassNameInDotForm = _methodEntry.getClassName().replace('/',
              '.');
      if (entryClassNameInDotForm.startsWith("scala.Tuple2")) {
          entryClassNameInDotForm = "scala.Tuple2";
      }
      final Set<ClassModel> matchingClassModels = new HashSet<ClassModel>();

      if (target == null) {
         // Look for member obj accessor calls
         for (final ClassModel memberObjClass : objectArrayFieldsClasses) {

           String memberObjClassName = memberObjClass.getClassWeAreModelling().getName();
           if (memberObjClassName.equals(entryClassNameInDotForm)) {
               MethodModel hardCoded = lookForHardCodedMethod(_methodEntry,
                   memberObjClass, guess);
               if (hardCoded != null) return hardCoded;

               target = memberObjClass.getMethod(_methodEntry, false);
               if (target != null) {
                  break;
               }
            } else {
                if (memberObjClass.classNameMatches(entryClassNameInDotForm)) {
                    matchingClassModels.add(memberObjClass);
                }
            }
         }
      }

      if (target == null) {
          for (Map.Entry<Integer, ClassModel> entry : internalParallelClassModels.entrySet()) {
              final ClassModel model = entry.getValue();
              String modelName = model.getClassWeAreModelling().getName();
              if (modelName.equals(entryClassNameInDotForm)) {
                  target = model.getMethod(_methodEntry, false);
                  if (target != null) {
                      break;
                  }
              }
          }
      }

      if (target == null) {
          for (ClassModel possibleMatch : matchingClassModels) {
               MethodModel hardCoded = lookForHardCodedMethod(_methodEntry,
                   possibleMatch, guess);
               if (hardCoded != null) return hardCoded;

               target = possibleMatch.getMethod(_methodEntry, false);
               if (target != null) {
                  break;
               }
          }
      }

      if (target != null) {
         for (final MethodModel m : calledMethods) {
            if (m.getMethod() == target) {
               if (logger.isLoggable(Level.FINE)) {
                  logger.fine("selected from called methods = " + m.getName());
               }

               return m;
            }
         }
      }

      // Search for static calls to other classes
      for (MethodModel m : calledMethods) {
         if (logger.isLoggable(Level.FINE)) {
            logger.fine("Searching for call target: " +
                    _methodEntry.toString() + " in " + m.getName());
         }
         if (m.getMethod().getName().equals(_methodEntry.getMethodName())
               && m.getMethod().getDescriptor().equals(_methodEntry.getMethodSig())) {
            if (logger.isLoggable(Level.FINE)) {
               logger.fine("Found " + m.getMethod().getClassModel()
                       .getClassWeAreModelling().getName() + "." +
                       m.getMethod().getName() + " " +
                       m.getMethod().getDescriptor());
            }
            return m;
         }
      }

      assert target == null : "Should not have missed a method in calledMethods";

      return null;
   }

   Entrypoint cloneForKernel(Object _k) throws AparapiException {
      try {
         Entrypoint clonedEntrypoint = (Entrypoint) clone();
         clonedEntrypoint.kernelInstance = _k;
         return clonedEntrypoint;
      } catch (CloneNotSupportedException e) {
         throw new AparapiException(e);
      }
   }
}
