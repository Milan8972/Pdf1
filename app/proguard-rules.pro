# ProGuard / R8 rules for PaperPilot — All-in-One PDF Editor

# Razorpay Android Checkout SDK keep rules
-keepattributes *Annotation*
-dontwarn com.razorpay.**
-keep class com.razorpay.** { *; }
-optimizations !method/inlining/*
-keepclasseswithmembers class * {
  public void onPayment*(...);
}
-keepclassmembers class * implements com.razorpay.PaymentResultWithDataListener {
  public void onPaymentSuccess(java.lang.String, com.razorpay.PaymentData);
  public void onPaymentError(int, java.lang.String, com.razorpay.PaymentData);
}

# Firebase Firestore @Keep models
-keep @androidx.annotation.Keep class * { *; }
