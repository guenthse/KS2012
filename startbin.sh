#!/bin/bash

# Starten des Beispielprogramms ohne Groovy-Installation
# Zuerst muss das jar-File KS_Praktikum.jar mit "unzip KS_Praktikum.jar" entpackt werden
# . startbin.sh [-e environment]
java -cp ".:libs/groovy-all-2.0.0.jar:libs/jpcap.jar:libs/commons-cli-1.2.jar" praktikum/beispiele/beispiel1/HttpBeispiel -p"praktikum/beispiele/" $*
