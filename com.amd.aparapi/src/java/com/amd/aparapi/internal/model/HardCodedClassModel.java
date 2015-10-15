package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.LinkedList;
import java.util.Iterator;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;

/*
 * When adding a new hard coded class model it is generally necessary to both
 * create a child of this HardCodedClassModel class and to insert some
 * type-specific bits in the KernelWriter logic.
 */
public abstract class HardCodedClassModel extends ClassModel {
    private final List<HardCodedMethodModel> methods;
    protected final TypeParameters paramDescs;

    public HardCodedClassModel(Class<?> clazz,
            List<HardCodedMethodModel> methods, List<AllFieldInfo> fields,
            String... paramDescs) {
        this.clazz = clazz;
        this.methods = methods;
        this.paramDescs = new TypeParameters(paramDescs);

        int id = 0;
        for (AllFieldInfo f : fields) {
            this.structMembers.add(new FieldNameInfo(f.name, f.desc, f.className));
            this.structMemberInfo.add(new FieldDescriptor(id, f.typ, f.name, f.offset));
            id++;
        }
    }

    // All subclasses must call this at the end of their constructor
    protected void initMethodOwners() {
        for (HardCodedMethodModel m : methods) {
          m.setOwnerMangledName(getMangledClassName());
        }
    }

    public TypeParameters getTypeParamDescs() {
        return paramDescs;
    }

    public List<HardCodedMethodModel> getMethods() {
      return methods;
    }

    public abstract String getDescriptor();
    public abstract List<String> getNestedTypeDescs();
    public abstract boolean merge(HardCodedClassModel other);
    public abstract int calcTotalStructSize(Entrypoint entryPoint);

    @Override
    public MethodModel checkForHardCodedMethods(String name, String desc,
            String templateGuess)
        throws AparapiException {
      return getMethodModel(name, desc, templateGuess);
    }

    private boolean isSubclassOf(String target, String superclass) {
        if (target.equals(superclass)) {
            return true;
        }

        if (target.startsWith("L") && superclass.equals("Ljava/lang/Object;")) {
            return true;
        }

        return false;
    }

    private static String[] parseParamTypesFromDesc(String params) {
        List<String> paramsList = new LinkedList<String>();
        int start = 0;
        int index = 0;
        int nesting = 0;
        while (index < params.length()) {
            if (params.charAt(index) == 'I' || params.charAt(index) == 'F' ||
                    params.charAt(index) == 'D') {
                if (nesting == 0) {
                    if (start != index) throw new RuntimeException();
                    paramsList.add(params.substring(start, index + 1));
                    start = index + 1;
                }
            } else if (params.charAt(index) == 'L' ||
                    params.charAt(index) == '<') {
                nesting++;
            } else if (params.charAt(index) == ';' ||
                    params.charAt(index) == '>') {
                nesting--;
                if (nesting == 0) {
                    paramsList.add(params.substring(start, index + 1));
                    start = index + 1;
                }
            }
            index++;
        }

        String[] arr = new String[paramsList.size()];
        for (int i = 0; i < paramsList.size(); i++) {
            arr[i] = paramsList.get(i);
        }
        return arr;
    }

    private static String[] extractTopLevelTypes(String guess) {
        guess = guess.substring(guess.indexOf("(") + 1, guess.length() - 1);
        List<String> acc = new LinkedList<String>();

        int index = 0;
        int start = 0;
        int nesting = 0;
        while (index < guess.length()) {
            if (guess.charAt(index) == '(') {
                nesting++;
            } else if (guess.charAt(index) == ')') {
                nesting--;
            } else if (guess.charAt(index) == ',' && nesting == 0) {
                acc.add(guess.substring(start, index));
                start = index + 1;
            }
            index++;
        }
        acc.add(guess.substring(start));

        String[] result = new String[acc.size()];
        for (int i = 0; i < acc.size(); i++) {
            result[i] = acc.get(i);
        }
        return result;
    }

    private boolean areSignaturesCompatible(String mySig, String lookingForSig,
            String lookingForMethodName, String templateGuess) {
        // if (lookingForMethodName.equals("<init>")) {
        //     return true;
        // }

        String specificParams = mySig.substring(mySig.indexOf('(') + 1);
        specificParams = specificParams.substring(0, specificParams.indexOf(')'));

        String broadParams = lookingForSig.substring(lookingForSig.indexOf('(') + 1);
        broadParams = broadParams.substring(0, broadParams.indexOf(')'));

        String[] specificParamsSplit = parseParamTypesFromDesc(specificParams);
        String[] broadParamsSplit = parseParamTypesFromDesc(broadParams);

        String specificReturn = mySig.substring(mySig.lastIndexOf(')') + 1);
        String broadReturn = lookingForSig.substring(lookingForSig.lastIndexOf(')') + 1);

        if (specificParamsSplit.length != broadParamsSplit.length) return false;

        if (isSubclassOf(specificReturn, broadReturn)) {

            if (templateGuess != null) {

                String[] topLevelTypes = extractTopLevelTypes(templateGuess);

                if (topLevelTypes.length != specificParamsSplit.length) {
                    throw new RuntimeException("topLevelTypes.length=" +
                            topLevelTypes.length +
                            " specificParamsSplit.length=" +
                            specificParamsSplit.length);
                }

                for (int i = 0; i < specificParamsSplit.length; i++) {
                    String s = specificParamsSplit[i];
                    if (s.indexOf("<") != -1) {
                        s = s.substring(0, s.indexOf("<"));
                    }
                    if (s.charAt(0) == 'L') {
                        s = s.substring(1);
                    }
                    if (s.charAt(s.length() - 1) == ';') {
                        s = s.substring(0, s.length() - 1);
                    }
                    s = s.replace('.', '/');

                    String g = topLevelTypes[i];
                    if (g.indexOf("(") != -1) {
                        g = g.substring(0, g.indexOf("("));
                    }

                    if (!isSubclassOf(s, g)) {
                        return false;
                    }
                }

                return true;
            } else {

                boolean allBroadParamsAreObjs = broadParamsSplit.length > 0;
                for (int i = 0; i < broadParamsSplit.length && allBroadParamsAreObjs; i++) {
                    if (!broadParamsSplit[i].equals("Ljava/lang/Object;")) {
                        allBroadParamsAreObjs = false;
                    }
                }

                if (allBroadParamsAreObjs) {
                    /*
                     * The target method being entirely an object-based signature
                     * limits what we can do. Scala seems to not emit these types of
                     * signatures when all of the parameters are primitives, so we
                     * just return false if we find specific is all primitive.
                     */
                    boolean allSpecificParamsArePrims = specificParamsSplit.length > 0;
                    for (int i = 0; i < specificParamsSplit.length &&
                            allSpecificParamsArePrims; i++) {
                        if (!specificParamsSplit[i].equals("Ljava/lang/Object;")) {
                            allSpecificParamsArePrims = false;
                        }
                    }
                    return !allSpecificParamsArePrims;
                } else {
                    for (int i = 0; i < specificParamsSplit.length; i++) {
                        String s = specificParamsSplit[i];
                        String b = broadParamsSplit[i];

                        if (!isSubclassOf(s, b)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        } else {
            return false;
        }
    }

    public MethodModel getMethodModel(String _name, String _signature, String templateGuess)
            throws AparapiException {
        for (HardCodedMethodModel method : methods) {
            if (method.getOriginalName().equals(_name) &&
                    areSignaturesCompatible(method.getDescriptor(), _signature,
                        _name, templateGuess)) {
                return method;
            }
        }
        return null;
    }


    @Override
    public MethodModel getMethodModel(String _name, String _signature)
            throws AparapiException {
        return getMethodModel(_name, _signature, null);
    }

    public static class AllFieldInfo {
        public final String name;
        public final String desc;
        public final String className;
        public int offset;
        public final TypeSpec typ;

        public AllFieldInfo(String name, String desc, String className, int offset) {
            this.name = name;
            this.desc = desc;
            this.className = className;
            this.offset = offset;

            boolean haveTypeSpec = false;
            for (TypeSpec t : TypeSpec.values()) {
                if (t.getShortName().equals(desc)) {
                    haveTypeSpec = true;
                }
            }

            if (haveTypeSpec) {
                this.typ = TypeSpec.valueOf(desc);
            } else {
                this.typ = TypeSpec.O;
            }
        }
    }

    public static class TypeParameters implements Comparable<TypeParameters>, Iterable<String> {
        private final List<String> paramDescs = new LinkedList<String>();

        public TypeParameters(String... paramDescs) {
            for (String d : paramDescs) {
                this.paramDescs.add(d);
            }
        }

        public TypeParameters(List<String> paramDescs) {
            for (String d : paramDescs) {
                this.paramDescs.add(d);
            }
        }

        public String get(int index) {
            return paramDescs.get(index);
        }

        public int size() {
            return paramDescs.size();
        }

        @Override
        public Iterator<String> iterator() {
            return paramDescs.iterator();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TypeParameters) {
                TypeParameters other = (TypeParameters)obj;
                Iterator<String> otherIter = other.paramDescs.iterator();
                Iterator<String> thisIter = paramDescs.iterator();
                while (otherIter.hasNext() && thisIter.hasNext()) {
                    String otherEle = otherIter.next();
                    String thisEle = thisIter.next();
                    if (!otherEle.equals(thisEle)) {
                        return false;
                    }
                }

                if (otherIter.hasNext() != thisIter.hasNext()) {
                    return false;
                }
                return true;
            }
            return false;
        }

        @Override
        public int compareTo(TypeParameters other) {
            if (this.equals(other)) return 0;
            return paramDescs.get(0).compareTo(other.paramDescs.get(0));
        }

        @Override
        public int hashCode() {
            return paramDescs.size();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[ ");
            for (String p : paramDescs) {
                sb.append(p + " ");
            }
            sb.append("]");
            return sb.toString();
        }
    }
}
