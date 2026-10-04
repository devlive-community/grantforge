// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

package org.devlive.grantforge.samples.notes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the sample notes application. */
@SpringBootApplication
public class NotesApplication
{
    /**
     * Starts the application.
     *
     * @param args Spring Boot arguments
     */
    public static void main(String[] args)
    {
        SpringApplication.run(NotesApplication.class, args);
    }
}
