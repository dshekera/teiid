/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.core.types.basic;

import org.teiid.core.types.DataTypeManager;
import org.teiid.core.types.Transform;
import org.teiid.core.types.TransformationException;

public class NumberToDoubleTransform extends Transform {
	
	private Class<?> sourceType;
	private boolean isNarrowing;
	private boolean isLossy;
	
	public NumberToDoubleTransform(Class<?> sourceType, boolean isNarrowing, boolean isLossy) {
		this.sourceType = sourceType;
		this.isNarrowing = isNarrowing;
		this.isLossy = isLossy;
	}
	
	@Override
	public Class<?> getSourceType() {
		return sourceType;
	}

	/**
	 * This method transforms a value of the source type into a value
	 * of the target type.
	 * @param value Incoming value of source type
	 * @return Outgoing value of target type
	 * @throws TransformationException if value is an incorrect input type or
	 * the transformation fails
	 */
	public Object transformDirect(Object value) throws TransformationException {
		if (isNarrowing) {
			checkValueRange(value, -Double.MAX_VALUE, Double.MAX_VALUE);
		}
		return Double.valueOf(((Number)value).doubleValue());
	}

	/**
	 * Type of the outgoing value.
	 * @return Target type
	 */
	public Class<?> getTargetType() {
		return DataTypeManager.DefaultDataClasses.DOUBLE;
	}
	
	@Override
	public boolean isExplicit() {
		return isNarrowing || isLossy;
	}
	
}
