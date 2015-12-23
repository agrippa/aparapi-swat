package com.amd.aparapi.internal.model;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;

public class HardCodedClassModels implements Iterable<HardCodedClassModel> {
    private final Map<String, List<HardCodedClassModel>> hardCodedClassModels =
        new HashMap<String, List<HardCodedClassModel>>();

    public void addClassModelFor(Class<?> clz, HardCodedClassModel model) {
       if (!hardCodedClassModels.containsKey(clz.getName())) {
           hardCodedClassModels.put(clz.getName(),
                   new LinkedList<HardCodedClassModel>());
       }

       List<HardCodedClassModel> existing = hardCodedClassModels.get(clz.getName());
       for (HardCodedClassModel cm : existing) {
           if (cm.merge(model)) {
               return;
           }
       }
       existing.add(model);
    }

    public int size() { return hardCodedClassModels.size(); }

    public boolean haveClassModelFor(Class<?> clz) {
        return hardCodedClassModels.containsKey(clz.getName());
    }

    public HardCodedClassModel getClassModelFor(String className,
          HardCodedClassModelMatcher matcher) {
       if (hardCodedClassModels.containsKey(className)) {
           List<HardCodedClassModel> classModels = hardCodedClassModels.get(
                   className);

           matcher.checkPreconditions(classModels);

           for (HardCodedClassModel model : classModels) {
               if (matcher.matches(model)) {
                   return model;
               }
           }
       }
       return null;
    }

    @Override
    public Iterator<HardCodedClassModel> iterator() {
        List<HardCodedClassModel> accClassModels =
            new LinkedList<HardCodedClassModel>();
        for (Map.Entry<String, List<HardCodedClassModel>> entry :
                hardCodedClassModels.entrySet()) {
            for (HardCodedClassModel model : entry.getValue()) {
                accClassModels.add(model);
            }
        }
        return accClassModels.iterator();
    }

    public abstract static class HardCodedClassModelMatcher {
        public abstract boolean matches(HardCodedClassModel model);
        public abstract void checkPreconditions(List<HardCodedClassModel> classModels);
    }

    public static class DescMatcher extends HardCodedClassModelMatcher {
         public final String[] desc;

         public DescMatcher(String[] desc) {
             this.desc = desc;
         }

         @Override
         public void checkPreconditions(List<HardCodedClassModel> classModels) { }

         @Override
         public boolean matches(HardCodedClassModel model) {
             TypeParameters paramDescs = model.getTypeParamDescs();
             if (paramDescs.size() == desc.length) {
                 int index = 0;
                 for (String d : paramDescs) {
                     if (!d.equals(desc[index])) return false;
                     index++;
                 }
                 return true;
             }
             return false;
         }

         @Override
         public String toString() {
             StringBuilder sb = new StringBuilder();
             sb.append("DescMatcher[");
             boolean first = true;
             for (String d : desc) {
                 if (!first) sb.append(", ");
                 sb.append(d);
                 first = false;
             }
             sb.append("]");
             return sb.toString();
         }
    }

    public static class UnparameterizedMatcher extends HardCodedClassModelMatcher {

        @Override
        public void checkPreconditions(List<HardCodedClassModel> classModels) {
            if (classModels.size() != 1) {
              throw new RuntimeException("The UnparameterizedMatcher should " +
                      "only be called for unparameterized classes that only " +
                      "have one entry in the HardCodedClassModels mapping, " +
                      "this one seems to have " + classModels.size() +
                      " mappings");
            }
        }

        @Override
        public boolean matches(HardCodedClassModel model) {
            return true;
        }

        @Override
        public String toString() {
            return "UnparameterizedMatcher";
        }
    }

    public static class ShouldNotCallMatcher extends HardCodedClassModelMatcher {
        @Override
        public void checkPreconditions(List<HardCodedClassModel> classModels) { }

        @Override
        public boolean matches(HardCodedClassModel model) {
            throw new RuntimeException("This matcher should only be used on " +
                "types which are not parameterized");
        }

        @Override
        public String toString() {
            return "ShouldNotCall";
        }
    }
}
