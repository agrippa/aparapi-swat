package com.amd.aparapi.internal.model;

import java.util.Collection;
import java.util.Map;
import com.amd.aparapi.internal.writer.ScalaArrayParameter;

final class EntrypointKey{
   public static EntrypointKey of(String entrypointName, String descriptor,
           Collection<ScalaArrayParameter> params,
           HardCodedClassModels models, Map<String, String> config) {
      return new EntrypointKey(entrypointName, descriptor, params, models, config);
   }

   private String descriptor;

   private String entrypointName;

   private Collection<ScalaArrayParameter> params;

   private HardCodedClassModels models;

   private Map<String, String> config;

   private EntrypointKey(String entrypointName, String descriptor,
           Collection<ScalaArrayParameter> params,
           HardCodedClassModels models, Map<String, String> config) {
      this.entrypointName = entrypointName;
      this.descriptor = descriptor;
      this.params = params;
      this.models = models;
      this.config = config;
   }

   String getDescriptor() {
       return descriptor;
   }

   String getEntrypointName() {
       return entrypointName;
   }

   Collection<ScalaArrayParameter> getParams() {
       return params;
   }

   HardCodedClassModels getModels() {
       return models;
   }

   Map<String, String> getConfig() {
       return config;
   }

   @Override public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((descriptor == null) ? 0 : descriptor.hashCode());
      result = prime * result + ((entrypointName == null) ? 0 : entrypointName.hashCode());
      return result;
   }

   @Override public String toString() {
      return "EntrypointKey [entrypointName=" + entrypointName + ", descriptor=" + descriptor + "]";
   }

   @Override public boolean equals(Object obj) {
      if (this == obj)
         return true;
      if (obj == null)
         return false;
      if (getClass() != obj.getClass())
         return false;
      EntrypointKey other = (EntrypointKey) obj;
      if (descriptor == null) {
         if (other.descriptor != null)
            return false;
      } else if (!descriptor.equals(other.descriptor))
         return false;
      if (entrypointName == null) {
         if (other.entrypointName != null)
            return false;
      } else if (!entrypointName.equals(other.entrypointName))
         return false;
      return true;
   }
}
