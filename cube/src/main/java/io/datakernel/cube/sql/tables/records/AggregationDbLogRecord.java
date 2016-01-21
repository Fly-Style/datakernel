/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This class is generated by jOOQ
 */
package io.datakernel.cube.sql.tables.records;


import io.datakernel.cube.sql.tables.AggregationDbLog;

import javax.annotation.Generated;

import org.jooq.Field;
import org.jooq.Record5;
import org.jooq.Row5;
import org.jooq.impl.TableRecordImpl;


/**
 * This class is generated by jOOQ.
 */
@Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.6.2"
	},
	comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AggregationDbLogRecord extends TableRecordImpl<AggregationDbLogRecord> implements Record5<String, String, String, Integer, Long> {

	private static final long serialVersionUID = 1415414901;

	/**
	 * Setter for <code>aggregation_db_log.log</code>.
	 */
	public void setLog(String value) {
		setValue(0, value);
	}

	/**
	 * Getter for <code>aggregation_db_log.log</code>.
	 */
	public String getLog() {
		return (String) getValue(0);
	}

	/**
	 * Setter for <code>aggregation_db_log.partition</code>.
	 */
	public void setPartition(String value) {
		setValue(1, value);
	}

	/**
	 * Getter for <code>aggregation_db_log.partition</code>.
	 */
	public String getPartition() {
		return (String) getValue(1);
	}

	/**
	 * Setter for <code>aggregation_db_log.file</code>.
	 */
	public void setFile(String value) {
		setValue(2, value);
	}

	/**
	 * Getter for <code>aggregation_db_log.file</code>.
	 */
	public String getFile() {
		return (String) getValue(2);
	}

	/**
	 * Setter for <code>aggregation_db_log.file_index</code>.
	 */
	public void setFileIndex(Integer value) {
		setValue(3, value);
	}

	/**
	 * Getter for <code>aggregation_db_log.file_index</code>.
	 */
	public Integer getFileIndex() {
		return (Integer) getValue(3);
	}

	/**
	 * Setter for <code>aggregation_db_log.position</code>.
	 */
	public void setPosition(Long value) {
		setValue(4, value);
	}

	/**
	 * Getter for <code>aggregation_db_log.position</code>.
	 */
	public Long getPosition() {
		return (Long) getValue(4);
	}

	// -------------------------------------------------------------------------
	// Record5 type implementation
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Row5<String, String, String, Integer, Long> fieldsRow() {
		return (Row5) super.fieldsRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Row5<String, String, String, Integer, Long> valuesRow() {
		return (Row5) super.valuesRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Field<String> field1() {
		return AggregationDbLog.AGGREGATION_DB_LOG.LOG;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Field<String> field2() {
		return AggregationDbLog.AGGREGATION_DB_LOG.PARTITION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Field<String> field3() {
		return AggregationDbLog.AGGREGATION_DB_LOG.FILE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Field<Integer> field4() {
		return AggregationDbLog.AGGREGATION_DB_LOG.FILE_INDEX;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Field<Long> field5() {
		return AggregationDbLog.AGGREGATION_DB_LOG.POSITION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String value1() {
		return getLog();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String value2() {
		return getPartition();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String value3() {
		return getFile();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Integer value4() {
		return getFileIndex();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Long value5() {
		return getPosition();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AggregationDbLogRecord value1(String value) {
		setLog(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AggregationDbLogRecord value2(String value) {
		setPartition(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AggregationDbLogRecord value3(String value) {
		setFile(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AggregationDbLogRecord value4(Integer value) {
		setFileIndex(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AggregationDbLogRecord value5(Long value) {
		setPosition(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AggregationDbLogRecord values(String value1, String value2, String value3, Integer value4, Long value5) {
		value1(value1);
		value2(value2);
		value3(value3);
		value4(value4);
		value5(value5);
		return this;
	}

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a detached AggregationDbLogRecord
	 */
	public AggregationDbLogRecord() {
		super(AggregationDbLog.AGGREGATION_DB_LOG);
	}

	/**
	 * Create a detached, initialised AggregationDbLogRecord
	 */
	public AggregationDbLogRecord(String log, String partition, String file, Integer fileIndex, Long position) {
		super(AggregationDbLog.AGGREGATION_DB_LOG);

		setValue(0, log);
		setValue(1, partition);
		setValue(2, file);
		setValue(3, fileIndex);
		setValue(4, position);
	}
}
