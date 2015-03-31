package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;

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
    }

    public static Tuple2ClassModel create(String firstTypeDesc,
            String firstTypeClassName, String secondTypeDesc,
            String secondTypeClassName) {
        List<AllFieldInfo> fields = new ArrayList<AllFieldInfo>(2);
        fields.add(new AllFieldInfo("_1", firstTypeDesc, firstTypeClassName, -1));
        fields.add(new AllFieldInfo("_2", secondTypeDesc, secondTypeClassName, -1));

        List<HardCodedMethodModel> methods = new ArrayList<HardCodedMethodModel>();
        methods.add(new HardCodedMethodModel("_1$mcI$sp", "()" + firstTypeDesc,
              "", true, "_1"));
        methods.add(new HardCodedMethodModel("_2", "()" + secondTypeDesc, "",
              true, "_2"));

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
        return "scala_Tuple2_" + firstTypeClassName + "_" + secondTypeClassName;
    }
}
