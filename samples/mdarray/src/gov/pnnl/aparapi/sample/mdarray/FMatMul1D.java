package gov.pnnl.aparapi.sample.mdarray;
import com.amd.aparapi.Kernel;

class FMatMul1D extends Kernel{
   float[] A;

   float[] B;

   float[] C;

   int N;

   public FMatMul1D(float[] A, float[] B, float[] C, int N) {
      this.A = A;
      this.B = B;
      this.C = C;
      this.N = N;
   }

   @Override public void run() {
      int id = getGlobalId();
      int i = id / N;
      int j = id % N;
      for (int k = 0; k < N; k++) {
         C[i * N + j] += A[i * N + k] * B[k * N + j];
      }
   }
}
