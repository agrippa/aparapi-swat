package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public abstract class ScalaArrayParameter implements ScalaParameter {

    protected final String type;
    protected final String name;
    protected final Class<?> clazz;
    protected final DIRECTION dir;
    protected final List<String> typeParameterDescs;
    protected final List<Boolean> typeParameterIsObject;

    public ScalaArrayParameter(String fullSig, String name, DIRECTION dir) {
        this.name = name.replace('$', '_');
        this.clazz = null;
        this.dir = dir;

        this.typeParameterDescs = new LinkedList<String>();
        this.typeParameterIsObject = new LinkedList<Boolean>();

        if (fullSig.charAt(0) != '[') {
            throw new RuntimeException(fullSig);
        }

        String eleSig = fullSig.substring(1);
        if (eleSig.indexOf('<') != -1) {
            String topLevelType = eleSig.substring(0, eleSig.indexOf('<'));
            if (topLevelType.charAt(0) != 'L') {
                throw new RuntimeException(fullSig);
            }
            this.type = topLevelType.substring(1).replace('/', '.');

            String params = eleSig.substring(eleSig.indexOf('<') + 1, eleSig.lastIndexOf('>'));
            if (params.indexOf('<') != -1 || params.indexOf('>') != -1) {
                throw new RuntimeException("Do not support nested parameter templates: " + fullSig);
            }
            String[] tokens = params.split(",");
            for (int i = 0; i < tokens.length; i++) {
                String t = tokens[i];
                if (t.equals("I") || t.equals("F") || t.equals("D") || t.equals("B")) {
                    this.typeParameterDescs.add(t);
                    this.typeParameterIsObject.add(false);
                } else {
                    this.typeParameterDescs.add("L" + t.replace('/', '.') + ";");
                    this.typeParameterIsObject.add(true);
                }
            }
        } else {
            if (eleSig.equals("I")) {
                this.type = "int";
            } else if (eleSig.equals("D")) {
                this.type = "double";
            } else if (eleSig.equals("F")) {
                this.type = "float";
            } else if (eleSig.equals("B")) {
                this.type = "char";
            } else if (eleSig.startsWith("L")) {
                this.type = eleSig.substring(1, eleSig.length() - 1).replace('/', '.');
            } else {
                throw new RuntimeException(eleSig);
            }
        }
    }

    public ScalaArrayParameter(String type, Class<?> clazz, String name,
            DIRECTION dir) {
        this.type = type.trim();
        this.clazz = clazz;
        this.name = name.replace('$', '_');
        this.dir = dir;
        this.typeParameterDescs = new LinkedList<String>();
        this.typeParameterIsObject = new LinkedList<Boolean>();
    }

    public void addTypeParameter(String s, boolean isObject) {
        typeParameterDescs.add(s);
        typeParameterIsObject.add(isObject);
    }

    public String[] getDescArray() {
        String[] arr = new String[typeParameterDescs.size()];
        int index = 0;
        for (String param : typeParameterDescs) {
            arr[index] = param;
            index++;
        }
        return arr;
    }

    public String getTypeParameter(int i) {
        if (i < typeParameterDescs.size()) {
            return typeParameterDescs.get(i);
        } else {
            return null;
        }
    }

    public boolean typeParameterIsObject(int i) {
        if (i < typeParameterIsObject.size()) {
            return typeParameterIsObject.get(i);
        }
        return false;
    }

    @Override
    public DIRECTION getDir() {
        return dir;
    }

    public String getName() {
        return name;
    }

    @Override
    public Class<?> getClazz() {
        return clazz;
    }

    public String getType() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.replace('.', '_'));
        for (String typeParam : typeParameterDescs) {
            sb.append("_");
            if (typeParam.charAt(0) == 'L') {
                sb.append(KernelWriter.removeBadChars(typeParam.substring(1,
                                typeParam.length() - 1)));
            } else {
                sb.append(typeParam);
            }
        }
        return sb.toString();
    }

    private static String javaPrimitiveToCLPrimitive(String javaPrimitive) {
        if (javaPrimitive.equals("byte")) {
            return "char";
        } else {
            return javaPrimitive;
        }
    }

    public static ScalaArrayParameter createArrayParameterFor(String type,
            String name, DIRECTION dir) {
        type = type.trim();
        if (type.endsWith(" []")) {
            // type = "double []"
            final String elementType = javaPrimitiveToCLPrimitive(type.split(" ")[0]);
            return new ScalaArrayOfArraysParameter(type, name, dir, elementType);
        } else if (type.equals(KernelWriter.TUPLE2_CLASSNAME)) {
            return new ScalaTuple2ArrayParameter(type, name, dir);
        } else if (type.equals(KernelWriter.DENSEVECTOR_CLASSNAME)) {
            return new ScalaDenseVectorArrayParameter(type, name, dir);
        } else if (type.equals(KernelWriter.SPARSEVECTOR_CLASSNAME)) {
            return new ScalaSparseVectorArrayParameter(type, name, dir);
        } else {
            return new ScalaPrimitiveOrObjectArrayParameter(type, name, dir);
        }
    }

    public static ScalaArrayParameter createArrayParameterFor(String type,
            Class<?> clazz, String name, DIRECTION dir) {
        type = type.trim();
        if (type.endsWith(" []")) {
            // type = "double []"
            final String elementType = javaPrimitiveToCLPrimitive(type.split(" ")[0]);
            return new ScalaArrayOfArraysParameter(type, clazz, name, dir, elementType);
        } else if (type.equals(KernelWriter.TUPLE2_CLASSNAME)) {
            return new ScalaTuple2ArrayParameter(type, clazz, name, dir);
        } else if (type.equals(KernelWriter.DENSEVECTOR_CLASSNAME)) {
            return new ScalaDenseVectorArrayParameter(type, clazz, name, dir);
        } else if (type.equals(KernelWriter.SPARSEVECTOR_CLASSNAME)) {
            return new ScalaSparseVectorArrayParameter(type, clazz, name, dir);
        } else {
            return new ScalaPrimitiveOrObjectArrayParameter(type, clazz, name, dir);
        }
    }

    @Override
    public String toString() {
        return "[" + type + " " + name + ", clazz=" + clazz + "]";
    }
}
