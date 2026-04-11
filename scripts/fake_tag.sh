#!/bin/bash


# 1. Definiujemy funkcję sprzątającą
cleanup() {
    echo -e "\n[!] Przechwycono CTRL+C (Sygnał SIGINT)."
    echo "Trwa sprzątanie: Wyłączam rozgłaszanie taga..."
    bluetoothctl advertise off > /dev/null
    echo "Zakończono pomyślnie. Cześć!"
    exit 0
}

# 2. Zakładamy pułapkę (trap). Gdy system wyśle SIGINT, odpal funkcję 'cleanup'
trap cleanup SIGINT

echo "Rozpoczynam symulację tagow BLE na Ubuntu..."
echo "Aby przerwać, wciśnij CTRL+C"

# Upewnij się, że radio jest włączone
bluetoothctl power on > /dev/null
bluetoothctl system-alias "TEST_TAG_312" > /dev/null

while true
do
    echo "[$(date +'%T')] TAG ON (Laptop is advertising)"
    bluetoothctl advertise on > /dev/null
    
    # Reklamuj się przez 3 sekundy
    sleep 3 &
    wait $! # 'wait' pozwala na natychmiastowe przerwanie sleepa przez trap
    
    echo "[$(date +'%T')] TAG OFF (no broadasting)"
    bluetoothctl advertise off > /dev/null
    
    # Odczekaj 5 sekund przed kolejnym cyklem
    sleep 5 &
    wait $!
done
