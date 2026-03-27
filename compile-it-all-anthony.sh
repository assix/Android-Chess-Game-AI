#!/bin/bash

# Run the build
./gradlew assembleDebug

# Check if the build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "================================================================"
    echo "✅ BUILD SUCCESSFUL"
    echo "================================================================"
    echo "📂 The new APK is located at:"
    echo "   app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "➡️  Suggested command to move and rename it:"
    echo "   mv app/build/outputs/apk/debug/app-debug.apk MyApkChess_v0.6.apk"
    echo ""
    echo "☁️  Test on your Android:"
    echo "   1. Upload the APK to Google Drive: https://drive.google.com/drive/folders/1Y1qij3gzu2wCnc_kN_unhhj0hIBNQ5Bp"
    echo "   2. Install and test it on your Samsung Galaxy S24 Ultra."
    echo ""
    echo "🐙 If it works perfectly, push the code to GitHub:"
    echo "   git add ."
    echo "   git commit -m \"Added Features, Fixed Bugs and improved ai algorithm \""
    echo "   git push"
    echo ""
    echo "🚀 Finally, submit the new app to GitHub Releases:"
    echo "   Go to: https://github.com/assix/Android-Chess-Game-AI/releases"
    echo "   Create a new release tagged 'v0.6', attach MyApkChess_v0.6.apk, and publish."
    echo "================================================================"
else
    echo "❌ BUILD FAILED. Check the errors above."
fi