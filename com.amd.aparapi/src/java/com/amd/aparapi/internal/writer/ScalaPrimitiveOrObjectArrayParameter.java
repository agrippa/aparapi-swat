package com.amd.aparapi.internal.writer;

import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

/*
 * Represents either a built-in primitive or a user-defined object of primitive
 * fields.
 */
public class ScalaPrimitiveOrObjectArrayParameter extends ScalaArrayParameter {
    public ScalaPrimitiveOrObjectArrayParameter(String fullSig, String name, DIRECTION dir) {
        super(fullSig, name, dir);
    }

    public ScalaPrimitiveOrObjectArrayParameter(String type, Class<?> clazz, String name,
            DIRECTION dir) {
        super(type, clazz, name, dir);
    }

    @Override
    public String getInputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.IN) {
            throw new RuntimeException();
        }

        if (writer.multiInput) {
            final String typeName = type.replace('.', '_');
            return "__global " + typeName + "* restrict " + name +
                ", __global " + typeName + "* restrict " + name +
                "_offset, __global " + typeName + "* restrict " + name +
                "_length";
        } else {
            return "__global " + type.replace('.', '_') + "* restrict " + name;
        }
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.OUT) {
            throw new RuntimeException();
        }

        return "__global " + type.replace('.', '_') + "* restrict " + name;
    }

    @Override
    public String getStructString(KernelWriter writer) {
        return "__global " + type.replace('.', '_') + "* " + name;
    }

    @Override
    public String getInputInitString(KernelWriter writer, String src) {
        return "";
    }

    @Override
    public String getGlobalInitString(KernelWriter writer) {
        if (dir != DIRECTION.IN) {
            throw new RuntimeException();
        }

        if (writer.multiInput) {
            return "this->" + name + " = " + name + "[i]";
        } else {
            return "this->" + name + " = " + name;
        }
    }
}
