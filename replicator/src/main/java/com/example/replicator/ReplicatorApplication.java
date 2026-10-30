
package com.example.replicator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcAutoConfiguration;
import org.springframework.context.annotation.Bean;

import com.example.replicator.DCT.DCT;

import java.util.Scanner;

@SpringBootApplication(exclude = {BatchJdbcAutoConfiguration.class})
public class ReplicatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReplicatorApplication.class, args);
    }

    @Bean
    CommandLineRunner run(
            SeedingService seedingService,
            DCT dct,
            KafkaListenerService kafkaListenerService) {

        return args -> {

            if (args.length == 0) {
                System.out.println(
                    "Usage: <DCT|seed|scheduleSeed> [table]"
                );
                return;
            }

            String operation = args[0];
            String table = args.length > 1 ? args[1] : "";

            /* 
            if (args.length > 2) {
                System.out.println(
                    "Too many arguments. Usage: <DCT|seed|listen> [table]"
                );
                return;
            }*/

            if (!operation.equals("listen") && table.isBlank()) {
                System.out.println(
                    "Table name is required for " + operation
                );
                return;
            }

            seedingService.setTable(table);

            kafkaListenerService.start();

            switch (operation) {

                case "DCT" -> {
                    DCT.ComparisonResult result =
                        seedingService.compare();

                    dct.printComparisonResults(result);

                    System.out.println(
                        "Comparison completed for table: " + table
                    );

                    System.out.print(
                        "Do you want to seed the target? (yes/no): "
                    );
                    System.out.flush();

                    Scanner scanner = new Scanner(System.in);
                    String choice = scanner.hasNextLine()
                        ? scanner.nextLine().trim()
                        : "no";

                    if (choice.equalsIgnoreCase("yes")
                            || choice.equalsIgnoreCase("y")) {

                        seedingService.seed(result);

                        System.out.println(
                            "Seeding completed for table: " + table
                        );
                    }
                }

                case "seed" -> {
                    DCT.ComparisonResult result =
                        seedingService.compare();

                    seedingService.seed(result);

                    System.out.println(
                        "Seeding completed for table: " + table
                    );
                }

                case "scheduleSeed" -> {

                    long initialDelay = Long.parseLong(args[2]);
                    long repeatInterval = Long.parseLong(args[3]);
                
                    seedingService.setTable(table);
                
                    // Schedule Kafka shutdown and recurring seeding
                    seedingService.scheduleSeed(
                            initialDelay,
                            repeatInterval
                    );
                }
            }
        };
    }
}