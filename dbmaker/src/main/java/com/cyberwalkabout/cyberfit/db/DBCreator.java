package com.cyberwalkabout.cyberfit.db;

import com.cyberwalkabout.cyberfit.config.Config;
import com.cyberwalkabout.cyberfit.db.sqlite.schema.DBSchema;

import java.nio.file.Path;

/**
 * This interface represents component which is responsible to create database out of provided config
 *
 * @author Andrii Kovalov
 */
public interface DBCreator {

    void createDatabase(Config config, DBSchema dbSchema, Path dbPath) throws DBException;
}
