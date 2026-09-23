plugins { `java-library` }
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.apache.pdfbox:pdfbox:3.0.4")
}
