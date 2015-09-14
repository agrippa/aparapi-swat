package com.amd.aparapi.internal.writer;

import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaTuple2ArrayParameter extends ScalaArrayParameter {
    public ScalaTuple2ArrayParameter(String fullSig, String name, DIRECTION dir) {
        super(fullSig, name, dir);
    }

    public ScalaTuple2ArrayParameter(String type, Class<?> clazz, String name,
            DIRECTION dir) {
        super(type, clazz, name, dir);
    }

    protected String getParameterStringFor(KernelWriter writer, int field) {
        final String param;
        if (!typeParameterIsObject(field)) {
            param = "__global " + ClassModel.convert(
                    typeParameterDescs.get(field), "", true) + "* " + name + "_" + (field + 1);
        } else {
            String fieldDesc = typeParameterDescs.get(field);
            if (fieldDesc.charAt(0) != 'L' ||
                    fieldDesc.charAt(fieldDesc.length() - 1) != ';') {
                throw new RuntimeException("Invalid object signature \"" + fieldDesc + "\"");
            }
            fieldDesc = fieldDesc.substring(1, fieldDesc.length() - 1);
            if (fieldDesc.equals(KernelWriter.DENSEVECTOR_CLASSNAME)) {
                final ScalaDenseVectorArrayParameter tmp =
                    new ScalaDenseVectorArrayParameter(fieldDesc,
                            name + "_" + (field + 1), getDir());
                if (getDir() == DIRECTION.IN) {
                    param = tmp.getInputParameterString(writer);
                } else {
                    param = tmp.getOutputParameterString(writer);
                }
            } else if (fieldDesc.equals(KernelWriter.SPARSEVECTOR_CLASSNAME)) {
                final ScalaSparseVectorArrayParameter tmp =
                    new ScalaSparseVectorArrayParameter(fieldDesc,
                            name + "_" + (field + 1), getDir());
                if (getDir() == DIRECTION.IN) {
                    param = tmp.getInputParameterString(writer);
                } else {
                    param = tmp.getOutputParameterString(writer);
                }
            } else {
                param = "__global " + fieldDesc.replace('.', '_') + "* " + name +
                    "_" + (field + 1);
            }
        }
        return param;
    }

    @Override
    public String getInputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.IN) {
            throw new RuntimeException();
        }

        final String firstParam = getParameterStringFor(writer, 0);
        final String secondParam = getParameterStringFor(writer, 1);
        String containerParam = "__global " + getType() + " *" + name;
        return firstParam + ", " + secondParam + ", " + containerParam;
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.OUT) {
            throw new RuntimeException();
        }

        final String firstParam = getParameterStringFor(writer, 0);
        final String secondParam = getParameterStringFor(writer, 1);
        return firstParam + ", " + secondParam;
    }

    @Override
    public String getStructString(KernelWriter writer) {
        if (dir == DIRECTION.OUT) {
            return getParameterStringFor(writer, 0) + "; " +
                getParameterStringFor(writer, 1) + "; ";
        } else {
            return "__global " + getType() + " *" + name;
        }
    }

    private static String getAssignStringFor(boolean parameterIsObject, int index,
            String desc, boolean isRef, String target, String src) {
        if (index < 1 || index > 2) {
            throw new RuntimeException("should be only 1 or 2 for the two " + 
                    "Tuple2 fields");
        }
        String connector = isRef ? "->" : ".";
        if (parameterIsObject) {
            return target + connector + "_" + index + " = " + src + "_" + index + " + i; ";
        } else {
            return target + connector + "_" + index + " = " + src + "_" + index + "[i]; ";
        }
    }

    @Override
    public String getInputInitString(KernelWriter writer, String src) {
        StringBuilder sb = new StringBuilder();
        sb.append(getAssignStringFor(typeParameterIsObject.get(0), 1,
                typeParameterDescs.get(0), true, "my_" + name, name) +
            getAssignStringFor(typeParameterIsObject.get(1), 2,
                    typeParameterDescs.get(1), true, "my_" + name, name));

        // e.g. typeParameterDescs = I Lorg.apache.spark.mllib.linalg.DenseVector;
        final String denseVectorDesc = "L" +
            KernelWriter.DENSEVECTOR_CLASSNAME + ";";
        final String sparseVectorDesc = "L" +
            KernelWriter.SPARSEVECTOR_CLASSNAME + ";";
        for (int i = 0; i < 2; i++) {
            if (typeParameterDescs.get(i).equals(denseVectorDesc)) {
                ScalaDenseVectorArrayParameter tmp =
                    new ScalaDenseVectorArrayParameter(
                            KernelWriter.DENSEVECTOR_CLASSNAME,
                            name + "->_" + (i + 1), getDir());
                sb.append(tmp.getInputInitString(writer, name + "_" + (i + 1)));
            } else if (typeParameterDescs.get(i).equals(sparseVectorDesc)) {
                throw new UnsupportedOperationException();
            }
        }

        return sb.toString();
    }

    @Override
    public String getGlobalInitString(KernelWriter writer) {
        if (dir != DIRECTION.IN) {
            throw new RuntimeException();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("this->" + name + " = " + name + "; ");
        sb.append("for (int i = 0; i < " + name + "__javaArrayLength; i++) { ");

        sb.append(getAssignStringFor(typeParameterIsObject.get(0), 1,
                typeParameterDescs.get(0), false, name + "[i]", name) +
            getAssignStringFor(typeParameterIsObject.get(1), 2,
                    typeParameterDescs.get(1), false, name + "[i]", name));

        sb.append(" } ");
        return sb.toString();
    }
}

