# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in the SDK tools proguard-defaults.txt file.

-keepclassmembers class com.aapp.coinflip.CoinFlipWidgetProvider {
    public void onReceive(android.content.Context, android.content.Intent);
}
