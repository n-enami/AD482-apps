package com.redhat.training.gardens.serde;

import com.redhat.training.gardens.model.Sensor;
import com.redhat.training.gardens.model.GardenStatus;
import com.redhat.training.gardens.model.SensorMeasurement;
import com.redhat.training.gardens.model.SensorMeasurementType;
import com.redhat.training.gardens.model.SensorMeasurementEnriched;
import com.redhat.training.gardens.event.LowHumidityDetected;
import com.redhat.training.gardens.event.LowTemperatureDetected;
import com.redhat.training.gardens.event.StrongWindDetected;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serde;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;


public class SerdeFactory {

    private static SerdeFactory instance;

    private static ObjectMapperSerde<SensorMeasurementEnriched> senserMeasurementEnrichedSerde;
    private static ObjectMapperSerde<LowTemperatureDetected> lowTemperatureEventSerde;
    private static ObjectMapperSerde<LowHumidityDetected> lowHumidityEventSerde;
    private static ObjectMapperSerde<StrongWindDetected> strongWindEventSerde;
    private static ObjectMapperSerde<GardenStatus> gardenStatusSerde;
    private static ObjectMapperSerde<Sensor> sensorSerde;
    private static ObjectMapperSerde<SensorMeasurement> sensorMeasurementSerde;

    private SerdeFactory(){
        senserMeasurementEnrichedSerde = new ObjectMapperSerde<>(SensorMeasurementEnriched.class);
        lowTemperatureEventSerde = new ObjectMapperSerde<>(LowTemperatureDetected.class);
        lowHumidityEventSerde = new ObjectMapperSerde<>(LowHumidityDetected.class);
        strongWindEventSerde = new ObjectMapperSerde<>(StrongWindDetected.class);
        gardenStatusSerde = new ObjectMapperSerde<>(GardenStatus.class);
        sensorSerde = new ObjectMapperSerde<>(Sensor.class);
        sensorMeasurementSerde = new ObjectMapperSerde<>(SensorMeasurement.class);        
    }

    public static synchronized SerdeFactory getInstance(){
        if(instance == null){
            instance = new SerdeFactory();
        }
        return instance;
    }
    
    public ObjectMapperSerde<SensorMeasurementEnriched> getSensorMeasurementEnrichedSerde() {
        return senserMeasurementEnrichedSerde;
    }

    public ObjectMapperSerde<LowTemperatureDetected> getLowTemperatureEventSerde() {
        return lowTemperatureEventSerde;
    }

    public ObjectMapperSerde<LowHumidityDetected> getLowHumidityEventSerde() {
        return lowHumidityEventSerde;
    }

    public ObjectMapperSerde<StrongWindDetected> getStrongWindEventSerde() {
        return strongWindEventSerde;
    }

    public ObjectMapperSerde<GardenStatus> getGardenStatusSerde() {
        return gardenStatusSerde;
    }

    public ObjectMapperSerde<Sensor> getSensorSerde() {
        return sensorSerde;
    }

    public ObjectMapperSerde<SensorMeasurement> getSensorMeasurementSerde() {
        return sensorMeasurementSerde;
    }

}
