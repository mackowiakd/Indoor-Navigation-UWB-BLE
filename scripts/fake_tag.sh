#!/bin/bash

echo "Rozpoczynam symulację tagow BLE na Ubuntu..."
echo "Aby przerwać, wciśnij CTRL+C"

# Upewnij się, że radio jest włączone
bluetoothctl power on > /dev/null

while true
do
    echo "[$(date +'%T')] TAG ON (Laptop krzyczy w eterzie)"
    bluetoothctl advertise on > /dev/null
    
    # Reklamuj się przez 3 sekundy
    sleep 3
    
    echo "[$(date +'%T')] TAG ON (Cisza w eterze)"
    bluetoothctl advertise off > /dev/null
    
    # Odczekaj 5 sekund przed kolejnym cyklem
    sleep 5
done