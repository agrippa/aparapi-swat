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

        if (BlockWriter.emitOcl) {
            return "__global " + type.replace('.', '_') + "* restrict " + name;
        } else {
            return type.replace('.', '_') + "* " + name;
        }
    }

    @Override
    public String getOutputParameterString(KernelWriter writer) {
        if (dir != DIRECTION.OUT) {
            throw new RuntimeException();
        }

        if (BlockWriter.emitOcl) {
            return "__global " + type.replace('.', '_') + "* restrict " + name;
        } else {
            return type.replace('.', '_') + "* " + name;
        }
    }

    @Override
    public String getStructString(KernelWriter writer) {
        if (writer.multiInput) {
            return type.replace('.', '_') + " " + name;
        } else {
            return (BlockWriter.emitOcl ? "__global " : "") + type.replace('.', '_') + "* " + name;
        }
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
            return "this_ptr->" + name + " = " + name + "[i]";
        } else {
            return "this_ptr->" + name + " = " + name;
        }
    }
}
