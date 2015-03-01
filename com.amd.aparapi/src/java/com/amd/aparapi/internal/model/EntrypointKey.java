package com.amd.aparapi.internal.model;

import java.util.Collection;
import com.amd.aparapi.internal.writer.BlockWriter.ScalaParameter;

final class EntrypointKey{
   public static EntrypointKey of(String entrypointName, String descriptor, Collection<ScalaParameter> params) {
      return new EntrypointKey(entrypointName, descriptor, params);
   }

   private String descriptor;

   private String entrypointName;

   private Collection<ScalaParameter> params;

   private EntrypointKey(String entrypointName, String descriptor, Collection<ScalaParameter> params) {
      this.entrypointName = entrypointName;
      this.descriptor = descriptor;
      this.params = params;
   }

   String getDescriptor() {
      return descriptor;
   }

   String getEntrypointName() {
      return entrypointName;
   }

   Collection<ScalaParameter> getParams() {
     return params;
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
