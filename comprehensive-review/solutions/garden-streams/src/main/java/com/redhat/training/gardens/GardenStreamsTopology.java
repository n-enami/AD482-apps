package com.redhat.training.gardens;

import java.time.Duration;

import javax.enterprise.inject.Produces;
import javax.enterprise.context.ApplicationScoped;

import com.redhat.training.gardens.model.Sensor;
import com.redhat.training.gardens.model.GardenStatus;
import com.redhat.training.gardens.model.SensorMeasurement;
import com.redhat.training.gardens.model.SensorMeasurementType;
import com.redhat.training.gardens.model.SensorMeasurementEnriched;
import com.redhat.training.gardens.event.LowHumidityDetected;
import com.redhat.training.gardens.event.LowTemperatureDetected;
import com.redhat.training.gardens.event.StrongWindDetected;

import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Branched;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.GlobalKTable;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Printed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.state.WindowStore;
import org.apache.kafka.streams.state.KeyValueStore;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;

import com.redhat.training.gardens.serde.SerdeFactory;
@ApplicationScoped
public class GardenStreamsTopology {
    public static final String SENSORS_TOPIC = "garden-sensors";
    public static final String GARDEN_STATUS_EVENTS_TOPIC = "garden-status-events";
    public static final String SENSOR_MEASUREMENTS_TOPIC = "garden-sensor-measurements-repl";
    public static final String ENRICHED_SENSOR_MEASUREMENTS_TOPIC = "garden-sensor-measurements-enriched";
    public static final String LOW_TEMPERATURE_EVENTS_TOPIC = "garden-low-temperature-events";
    public static final String LOW_HUMIDITY_EVENTS_TOPIC = "garden-low-humidity-events";
    public static final String STRONG_WIND_EVENTS_TOPIC = "garden-strong-wind-events";

    private static final double LOW_TEMPERATURE_THRESHOLD_CELSIUS = 5.0;
    private static final double LOW_HUMIDITY_THRESHOLD_PERCENT = 0.2;
    private static final double STRONG_WIND_THRESHOLD_MS = 10;

    // private final ObjectMapperSerde<SensorMeasurementEnriched> sensorMeasurementEnrichedSerde = new ObjectMapperSerde<>(SensorMeasurementEnriched.class);
    // private final ObjectMapperSerde<LowTemperatureDetected> lowTemperatureEventSerde = new ObjectMapperSerde<>(LowTemperatureDetected.class);
    // private final ObjectMapperSerde<LowHumidityDetected> lowHumidityEventSerde = new ObjectMapperSerde<>(LowHumidityDetected.class);
    // private final ObjectMapperSerde<StrongWindDetected> strongWindEventSerde = new ObjectMapperSerde<>(StrongWindDetected.class);
    // private final ObjectMapperSerde<GardenStatus> gardenStatusSerde = new ObjectMapperSerde<>(GardenStatus.class);

    // private final ObjectMapperSerde<Sensor> sensorSerde = new ObjectMapperSerde<>(Sensor.class);
    // private final ObjectMapperSerde<SensorMeasurement> sensorMeasurementSerde = new ObjectMapperSerde<>(SensorMeasurement.class);

    SerdeFactory serdeFactory = SerdeFactory.getInstance();

    @Produces
    public Topology build() {
        StreamsBuilder builder = new StreamsBuilder();

        // // TODO: Read sensors
        // GlobalKTable<Integer, Sensor> sensors = builder.globalTable(
        //     SENSORS_TOPIC,
        //     Consumed.with(Serdes.Integer(), sensorSerde));

        // // TODO: Read sensor measurements
        // KStream<Integer, SensorMeasurement> sensorMeasurements = builder.stream(
        //     SENSOR_MEASUREMENTS_TOPIC,
        //     Consumed.with(Serdes.Integer(), sensorMeasurementSerde));

        // // TODO: Join measurements with sensor table
        // KStream<Integer, SensorMeasurementEnriched> enrichedSensorMeasurements = sensorMeasurements
        // .join(
        //     sensors,
        //     (sensorId, measurement) -> sensorId,
        //     (measurement, sensor) -> new SensorMeasurementEnriched(
        //         measurement, sensor));

        // // TODO: Send enriched measurements to topic
        // enrichedSensorMeasurements.to(
        //     ENRICHED_SENSOR_MEASUREMENTS_TOPIC,
        //     Produced.with(Serdes.Integer(), sensorMeasurementEnrichedSerde));


        GlobalKTable<Integer, Sensor> sensors = builder.globalTable(
            SENSORS_TOPIC,
            Consumed.with(Serdes.Integer(), serdeFactory.getSensorSerde())
        );

        KStream<Integer, SensorMeasurement> sensorMeasurements = builder.stream(
            SENSOR_MEASUREMENTS_TOPIC,
            Consumed.with(Serdes.Integer(), serdeFactory.getSensorMeasurementSerde())
        );

        KStream<Integer, SensorMeasurementEnriched> enrichedSensorMeasurements = sensorMeasurements
            .join(
                sensors,
                (sensorId, measurement) -> sensorId,
                (measurement, sensor) -> new SensorMeasurementEnriched(
                    measurement, sensor
                )
            );

        enrichedSensorMeasurements.to(
            ENRICHED_SENSOR_MEASUREMENTS_TOPIC,
            Produced.with(Serdes.Integer(), serdeFactory.getSensorMeasurementEnrichedSerde())
        );

        enrichedSensorMeasurements.print(Printed.toSysOut());

        // TODO: split stream
        enrichedSensorMeasurements
            .split()
                .branch((sensorId, measurement) -> measurement.type.equals(SensorMeasurementType.TEMPERATURE),
                        Branched.withConsumer(this::processTemperature))
                .branch((sensorId, measurement) -> measurement.type.equals(SensorMeasurementType.HUMIDITY),
                        Branched.withConsumer(this::processHumidity))
                .branch((sensorId, measurement) -> measurement.type.equals(SensorMeasurementType.WIND),
                        Branched.withConsumer(this::processWind));

        // TODO: Aggregate enriched measurements
        enrichedSensorMeasurements
            .groupBy(
                (sensorId, measurement) -> measurement.gardenName,
                Grouped.with(Serdes.String(), serdeFactory.getSensorMeasurementEnrichedSerde())
            )
            .windowedBy(
                TimeWindows.of(Duration.ofMinutes(1)).advanceBy(Duration.ofMinutes(1))
            )
            .aggregate(
                GardenStatus::new,
                (gardenName, measurement, gardenStatus) ->
                    gardenStatus.updateWith(measurement),
                Materialized
                    .<String, GardenStatus, WindowStore<Bytes, byte[]>>as(
                            "garden-status-store"
                        )
                        .withKeySerde(Serdes.String())
                        .withValueSerde(serdeFactory.getGardenStatusSerde()))
            .toStream()
            .map((windowedGardenName, gardenStatus) -> new KeyValue<Void, GardenStatus>(
                null, gardenStatus))
            .to(
                GARDEN_STATUS_EVENTS_TOPIC,
                Produced.with(Serdes.Void(), serdeFactory.getGardenStatusSerde()));

        return builder.build();
    }

    // TODO: implement temperature processor
    private void processTemperature(KStream<Integer, SensorMeasurementEnriched> temperatureMeasurements) {
        temperatureMeasurements
            .filter((sensorId, measurement) -> measurement.value < LOW_TEMPERATURE_THRESHOLD_CELSIUS)
            .mapValues((measurement) -> new LowTemperatureDetected(measurement.gardenName, measurement.sensorId,
                    measurement.value, measurement.timestamp))
            .to(LOW_TEMPERATURE_EVENTS_TOPIC, Produced.with(Serdes.Integer(), serdeFactory.getLowTemperatureEventSerde()));
    }

    // TODO: implement humidity processor
    private void processHumidity(KStream<Integer, SensorMeasurementEnriched> humidityMeasurements) {
        humidityMeasurements
            .filter((sensorId, measurement) -> measurement.value < LOW_HUMIDITY_THRESHOLD_PERCENT)
            .mapValues((measurement) -> new LowHumidityDetected(measurement.gardenName, measurement.sensorId,
                    measurement.value, measurement.timestamp))
            .to(LOW_HUMIDITY_EVENTS_TOPIC, Produced.with(Serdes.Integer(), serdeFactory.getLowHumidityEventSerde()));
    }

    // TODO: implement wind processor
    private void processWind(KStream<Integer, SensorMeasurementEnriched> windMeasurements) {
        windMeasurements
        .filter((sensorId, measurement) -> measurement.value > STRONG_WIND_THRESHOLD_MS)
            .mapValues((measurement) -> new StrongWindDetected(measurement.gardenName, measurement.sensorId,
                    measurement.value, measurement.timestamp))
            .to(STRONG_WIND_EVENTS_TOPIC, Produced.with(Serdes.Integer(), serdeFactory.getStrongWindEventSerde()));
    }

}
