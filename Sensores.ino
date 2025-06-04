#include <LiquidCrystal_I2C.h>
#include <SPI.h>
#include <WiFi.h>
#include <Wire.h>
#include <ThingSpeak.h>  // Include ThingSpeak library

char ssid[] = "Wokwi-GUEST";
char pass[] = "";

LiquidCrystal_I2C lcd(0x27, 20, 3);

// Sensor ultrasonik
const int trigPin = 14;
const int echoPin = 12;

long duration;
int distance;
float fuelDepth;
float volume;
float fuelLevelPercentage;

// Dimensi tangki
float tankRadius = 118.5;
float tankLength = 455.4;
float maxVolume = PI * pow(tankRadius, 2) * tankLength;

int counter = 0;

// ThingSpeak
WiFiClient client;
#define THINGSPEAK_CHANNEL_ID 2979612
#define THINGSPEAK_API_KEY "ORVHU58ZBPKZOFGQ"

void setup() {
  Serial.begin(9600);
  Wire.begin(21, 22);  // SDA = D21, SCL = D22 pada ESP32
  lcd.init();
  lcd.backlight();
  delay(1000);
  lcd.print("Fuel Level");
  lcd.setCursor(0, 1);
  lcd.print("Connecting...");
  delay(2000);
  lcd.clear();
  Serial.print("Connecting to WiFi");
  WiFi.begin("Wokwi-GUEST", "", 6);
  while (WiFi.status() != WL_CONNECTED) {
    delay(100);
    Serial.print(".");
  }
  Serial.println(" Connected!");
  
  pinMode(trigPin, OUTPUT);
  pinMode(echoPin, INPUT);

  ThingSpeak.begin(client);  // Initialize ThingSpeak
}

void loop() {
  // Mengukur kedalaman bahan bakar
  digitalWrite(trigPin, LOW);
  delayMicroseconds(100);
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(100);
  digitalWrite(trigPin, LOW);

  duration = pulseIn(echoPin, HIGH);
  distance = duration * 0.034 / 2;

  fuelDepth = tankRadius * 2 - distance;
  if (fuelDepth < 0) fuelDepth = 0;
  if (fuelDepth > 2 * tankRadius) fuelDepth = 2 * tankRadius;

  float R = tankRadius;
  float h = fuelDepth;
  float segmentArea = R * R * acos((R - h) / R) - (R - h) * sqrt(2 * R * h - h * h);
  volume = tankLength * segmentArea;
  float volumeInLiters = volume / 1000.0;
  fuelLevelPercentage = (volume / maxVolume) * 100.0;

  // Kirim data ke ThingSpeak
  counter++;
  ThingSpeak.setField(1, fuelDepth);
  ThingSpeak.setField(2, volumeInLiters);
  ThingSpeak.setField(3, fuelLevelPercentage);
  /*// Tampilan data perjam dan per24jam 
  ThingSpeak.setField(4, fuelDepth);
  ThingSpeak.setField(5, fuelDepth);
  ThingSpeak.setField(6, volumeInLiters);
  ThingSpeak.setField(7, volumeInLiters);*/  
  ThingSpeak.writeFields(THINGSPEAK_CHANNEL_ID, THINGSPEAK_API_KEY);
  delay(100); // 15 seconds  
  

  // Tampilkan ke LCD
  lcd.setCursor(7, 0);
  lcd.print("Monitor");
  lcd.setCursor(0, 1);
  lcd.print("Depth: ");
  lcd.print(fuelDepth);
  lcd.print(" cm ");
  lcd.setCursor(0, 2);
  lcd.print("Vol: ");
  lcd.print(volumeInLiters, 2);
  lcd.print(" L  ");
  lcd.setCursor(0, 3);
  lcd.print("Level: ");
  lcd.print(fuelLevelPercentage, 1);
  lcd.print(" %   ");
  
}
