#!/bin/bash

# Starten des Beispielprogramms
groovy -cp "src:libs/jpcap.jar" src/praktikum/beispiele/beispiel1/HttpBeispiel.groovy  -p "src/praktikum/beispiele/" $*
