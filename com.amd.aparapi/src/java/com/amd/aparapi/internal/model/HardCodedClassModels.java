package com.amd.aparapi.internal.model;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

public class HardCodedClassModels implements Iterable<HardCodedClassModel> {
    private final Map<String, List<HardCodedClassModel>> hardCodedClassModels =
        new HashMap<String, List<HardCodedClassModel>>();

    public void addClassModelFor(Class<?> clz, HardCodedClassModel model) {
       if (!hardCodedClassModels.containsKey(clz.getName())) {
           hardCodedClassModels.put(clz.getName(),
                   new LinkedList<HardCodedClassModel>());
       }
       hardCodedClassModels.get(clz.getName()).add(model);
    }

    public HardCodedClassModel getClassModelFor(Class<?> clz) {
       return getClassModelFor(clz.getName());
    }

    public HardCodedClassModel getClassModelFor(String className) {
       if (hardCodedClassModels.containsKey(className)) {
           List<HardCodedClassModel> classModels = hardCodedClassModels.get(
                   className);
           for (HardCodedClassModel model : classModels) {
               if (model.matches()) {
                   return model;
               }
           }
       }
       throw new RuntimeException("Unable to find a matching hard coded class " +
               "model for clz=" + className);
    }

    public boolean hasClassModelFor(String className) {
        /*
         * TODO this will have to account for multiple classes for the same
         * top-level class name
         */
        return hardCodedClassModels.containsKey(className);
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
}
