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
package org.teiid.resource.adapter.infinispan.dsl.base;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.resource.ResourceException;
import javax.resource.spi.InvalidPropertyException;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.protostream.BaseMarshaller;
import org.infinispan.protostream.SerializationContext;
import org.jboss.modules.Module;
import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoadException;
import org.teiid.core.util.StringUtil;
import org.teiid.logging.LogConstants;
import org.teiid.logging.LogManager;
import org.teiid.resource.spi.BasicConnectionFactory;
import org.teiid.resource.spi.BasicManagedConnectionFactory;
import org.teiid.translator.infinispan.dsl.InfinispanPlugin;

import com.google.protobuf.Descriptors.DescriptorValidationException;


public abstract class AbstractInfinispanManagedConnectionFactory extends
		BasicManagedConnectionFactory {

	/**
	 */
	private static final long serialVersionUID = -4791974803005018658L;

	private enum CACHE_TYPE {
		USE_JNDI, REMOTE_SERVER_LISTS, REMOTE_HOT_ROD_PROPERTIES
	}
	
	private String remoteServerList = null;
	private String hotrodClientPropertiesFile = null;
	private String cacheJndiName = null;
	private Map<String, Class<?>> typeMap = null; // cacheName ==> ClassType
	private String cacheTypes = null;
	private Map<Class, BaseMarshaller> messageMarshallerList = null;
	
	private String protobinFile = null;
	private String messageMarshallers = null;
	private String messageDescriptor = null;
	
	private RemoteCacheManager cacheContainer = null;
	private Map<String, String> pkMap; // cacheName ==> pkey name
	private CACHE_TYPE cacheType;
	private String module;
	private ClassLoader cl;


	@Override
	public BasicConnectionFactory<InfinispanConnectionImpl> createConnectionFactory()
			throws ResourceException {
		
		if (protobinFile == null) {
			throw new InvalidPropertyException(
					InfinispanPlugin.Util
							.getString("InfinispanManagedConnectionFactory.invalidProtobinFile")); //$NON-NLS-1$			
		}

		if (messageMarshallers == null) {
			throw new InvalidPropertyException(
					InfinispanPlugin.Util
							.getString("InfinispanManagedConnectionFactory.invalidMessagenMarshallers")); //$NON-NLS-1$			
		}
		
		if (messageDescriptor == null) {
			throw new InvalidPropertyException(
					InfinispanPlugin.Util
							.getString("InfinispanManagedConnectionFactory.invalidMessageDescriptor")); //$NON-NLS-1$			
		}
		
		if (this.cacheTypes == null) {
			throw new InvalidPropertyException(
					InfinispanPlugin.Util
							.getString("InfinispanManagedConnectionFactory.cacheTypeMapNotSet")); //$NON-NLS-1$
		}

		if (remoteServerList == null
				&& hotrodClientPropertiesFile == null && cacheJndiName == null) {
			throw new InvalidPropertyException(
					InfinispanPlugin.Util
							.getString("InfinispanManagedConnectionFactory.invalidServerConfiguration")); //$NON-NLS-1$	
		}

		determineCacheType();
		if (cacheType == null) {
			throw new InvalidPropertyException(
					InfinispanPlugin.Util
							.getString("InfinispanManagedConnectionFactory.invalidServerConfiguration")); //$NON-NLS-1$			
		}
		
		/*
		 * the creation of the cacheContainer has to be done within the
		 * call to get the connection so that the classloader is driven
		 * from the caller.
		 */
		return new BasicConnectionFactory<InfinispanConnectionImpl>() {

			private static final long serialVersionUID = 1L;

			@Override
			public InfinispanConnectionImpl getConnection()
					throws ResourceException {
				
				AbstractInfinispanManagedConnectionFactory.this.createCacheContainer();

				return new InfinispanConnectionImpl(AbstractInfinispanManagedConnectionFactory.this);
			}
		};

	}

	/**
	 * Get the <code>cacheName:ClassName[,cacheName:ClassName...]</code> cache
	 * type mappings.
	 * 
	 * @return <code>cacheName:ClassName[,cacheName:ClassName...]</code> cache
	 *         type mappings
	 * @see #setCacheTypeMap(String)
	 */
	public String getCacheTypeMap() {
		return cacheTypes;
	}

	/**
	 * Set the cache type mapping
	 * <code>cacheName:ClassName[,cacheName:ClassName...]</code> that represent
	 * the root node class type for 1 or more caches available for access.
	 * 
	 * @param cacheTypeMap
	 *            the cache type mappings passed in the form of
	 *            <code>cacheName:ClassName[,cacheName:ClassName...]</code>
	 * @see #getCacheTypeMap()
	 */
	public void setCacheTypeMap(String cacheTypeMap) {
		this.cacheTypes = cacheTypeMap;
	}

	/**
	 * Sets the (optional) module(s) where the ClassName class is defined, that
	 * will be loaded <code> (module,[module,..])</code>
	 * 
	 * @param module
	 * @see #getModule
	 */
	public void setModule(String module) {
		this.module = module;
	}

	/**
	 * Called to get the module(s) that are to be loaded
	 * 
	 * @see #setModule
	 * @return String
	 */
	public String getModule() {
		return module;
	}
	
	/**
	 * Get the Protobin File Name
	 * 
	 * @return Name of the Protobin File
	 * @see #setProtobinFile(String)
	 */
	public String getProtobinFile() {
		return protobinFile;
	}

	/**
	 * Set the Google Protobin File name that describes the objects to be serialized.
	 * 
	 * @param protobinFile
	 *            the file name of the protobin file to use
	 * @see #getProtobinFile()
	 */
	public void setProtobinFile(String protobinFile) {
		this.protobinFile = protobinFile;
	}

	/**
	 * Get the Message Marshaller class names
	 * 
	 * @return String comma delimited, class names of Message Marshallers
	 * @see #setMessageMarshallers(String)
	 */
	public String getMessageMarshallers() {
		return messageMarshallers;
	}

	/**
	 * Set the Protobin Marshallers classname[,classname,..]
	 * 
	 * @param messageMarshallers
	 *            the class names of the marshallers to use
	 * @see #getMessageMarshallers()
	 */
	public void setMessageMarshallers(String messageMarshallers) {
		this.messageMarshallers = messageMarshallers;
	}
	
	/**
	 * Get the Message descriptor class name for the root object in cache
	 * 
	 * @return Message Descriptor name
	 * @see #setMessageDescriptor(String)
	 */
	public String getMessageDescriptor() {
		return messageDescriptor;
	}

	/**
	 * Set the name of the Message Descriptor
	 * 
	 * @param messageDescriptor
	 *            the name of the message descriptor
	 * @see #getMessageDescriptor()
	 */
	public void setMessageDescriptor(String messageDescriptor) {
		this.messageDescriptor = messageDescriptor;
	}	

	public String getPkMap(String cacheName) {
		return pkMap.get(cacheName);
	}

	public void setPkMap(Map<String, String> mapOfPKs) {
		pkMap = mapOfPKs;
	}

	public Map<String, Class<?>> getCacheNameClassTypeMapping() {
		return this.typeMap;
	}

	public void setCacheNameClassTypeMapping(Map<String, Class<?>> cacheType) {
		this.typeMap = cacheType;
	}

	public Class<?> getCacheType(String cacheName) {
		return this.typeMap.get(cacheName);
	}
	
	@SuppressWarnings("rawtypes")
	public Map<Class, BaseMarshaller> getMessageMarshallerList() {
		return this.messageMarshallerList;
	}

	/**
	 * Returns the <code>host:port[;host:port...]</code> list that identifies
	 * the remote servers to include in this cluster;
	 * 
	 * @return <code>host:port[;host:port...]</code> list
	 */
	public String getRemoteServerList() {
		return remoteServerList;
	}

	/**
	 * Set the list of remote servers that make up the Infinispan cluster. The
	 * servers must be Infinispan HotRod servers. The list must be in the
	 * appropriate format of <code>host:port[;host:port...]</code> that would be
	 * used when defining an Infinispan remote cache manager instance. If
	 * the value is missing, <code>localhost:11311</code> is assumed.
	 * 
	 * @param remoteServerList
	 *            the server list in appropriate
	 *            <code>server:port;server2:port2</code> format.
	 */
	public void setRemoteServerList(String remoteServerList) {
		this.remoteServerList = remoteServerList;
	}


	/**
	 * Get the name of the HotRod client properties file that should be used to
	 * configure a remoteCacheManager.
	 * 
	 * @return the name of the HotRod client properties file to be used to
	 *         configure remote cache manager
	 * @see #setHotRodClientPropertiesFile(String)
	 */
	public String getHotRodClientPropertiesFile() {
		return hotrodClientPropertiesFile;
	}

	/**
	 * Set the name of the HotRod client properties file that should be used to
	 * configure a remoteCacheManager.
	 * 
	 * @param propertieFileName
	 *            the name of the HotRod client properties file that should be
	 *            used to configure the remote cache manager
	 *            
	 * @see #getHotRodClientPropertiesFile()
	 */
	public void setHotRodClientPropertiesFile(String propertieFileName) {
		this.hotrodClientPropertiesFile = propertieFileName;
	}

	/**
	 * Get the JNDI Name of the cache.
	 * 
	 * @return JNDI Name of cache
	 */
	public String getCacheJndiName() {
		return cacheJndiName;
	}

	/**
	 * Set the JNDI name to a {@link Map cache} instance that should be used as
	 * this source.
	 * 
	 * @param jndiName
	 *            the JNDI name of the {@link Map cache} instance that should be
	 *            used
	 * @see #setCacheJndiName(String)
	 */
	public void setCacheJndiName(String jndiName) {
		this.cacheJndiName = jndiName;
	}

	public boolean isAlive() {
		return this.cacheContainer != null;
	}
	
	protected RemoteCacheManager getCacheContainer() {
		return this.cacheContainer;
	}
	
	protected void setCacheContainer(RemoteCacheManager rcm) {
		this.cacheContainer = rcm;
	}
	
	abstract protected SerializationContext getContext();
	
	protected ClassLoader getClassLoader() {
		return this.cl;
	}
	
	protected Class loadClass(String className) throws ResourceException {
		try {
			return Class.forName(className, true, getClassLoader());
		} catch (ClassNotFoundException e) {
			throw new ResourceException(e);
		}
	}

	@SuppressWarnings("rawtypes")
	protected synchronized ClassLoader loadClasses() throws ResourceException {

		cl = null;

		if (getModule() != null) {

			try {
				List<String> mods = StringUtil.getTokens(getModule(), ","); //$NON-NLS-1$
				for (String mod : mods) {

					Module x = Module.getContextModuleLoader().loadModule(
							ModuleIdentifier.create(mod));
					// the first entry must be the module associated with the
					// cache
					if (cl == null) {
						cl = x.getClassLoader();
					}
				}
			} catch (ModuleLoadException e) {
				throw new ResourceException(e);
			}

		} else {
			cl = this.getClass().getClassLoader();
		}
		
		List<String> types = StringUtil.getTokens(getCacheTypeMap(), ","); //$NON-NLS-1$

		Map<String, String> pkMap = new HashMap<String, String>(types.size());
		Map<String, Class<?>> tm = new HashMap<String, Class<?>>(types.size());

		for (String type : types) {
			List<String> mapped = StringUtil.getTokens(type, ":"); //$NON-NLS-1$
			if (mapped.size() != 2) {
				throw new InvalidPropertyException(
						InfinispanPlugin.Util
								.getString("InfinispanManagedConnectionFactory.invalidCacheTypeMap")); //$NON-NLS-1$ 
			}
			final String cacheName = mapped.get(0);
			String className = mapped.get(1);
			mapped = StringUtil.getTokens(className, ";"); //$NON-NLS-1$
			if (mapped.size() > 1) {
				className = mapped.get(0);
				pkMap.put(cacheName, mapped.get(1));
			}
			tm.put(cacheName, loadClass(className)); 

		}

		List<String> marshallers = StringUtil.getTokens(this.getMessageMarshallers(), ","); //$NON-NLS-1$
		messageMarshallerList = new HashMap<Class, BaseMarshaller>(marshallers.size());

		for (String mm : marshallers) {
			
			List<String> mapped = StringUtil.getTokens(mm, ":"); //$NON-NLS-1$
			if (mapped.size() != 2) {
				throw new InvalidPropertyException(
						InfinispanPlugin.Util
								.getString("InfinispanManagedConnectionFactory.invalidMarshallerMapping")); //$NON-NLS-1$ 
			}
			final String className = mapped.get(0);
			final String m = mapped.get(1);

			try {
				Object i = (loadClass(m)).newInstance();

				messageMarshallerList.put(loadClass(className), (BaseMarshaller) i); 		
			
			} catch (InstantiationException e) {
				throw new ResourceException(e);
			} catch (IllegalAccessException e) {	
				throw new ResourceException(e);
			} 
		}
		
		setCacheNameClassTypeMapping(Collections.unmodifiableMap(tm));
		setPkMap(Collections.unmodifiableMap(pkMap));

		return cl;

	}

	protected synchronized void createCacheContainer() throws ResourceException {
		if (getCacheContainer() != null)
			return;
		
		RemoteCacheManager cc = null;

		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(
					this.getClass().getClassLoader());
		
			ClassLoader classLoader = loadClasses();

			switch (cacheType) {
			case USE_JNDI:
				cc = createRemoteCacheWrapperFromJNDI(this.getCacheJndiName(), classLoader);
				break;
	
			case REMOTE_HOT_ROD_PROPERTIES:
				cc = createRemoteCacheWrapperFromProperties(classLoader);
				break;
	
			case REMOTE_SERVER_LISTS:
				cc = createRemoteCacheWrapperFromServerList(classLoader);
				break;
	
			}

			setCacheContainer(cc);
			registerMarshallers(getContext(), classLoader);

		
		} finally {
			Thread.currentThread().setContextClassLoader(cl);
		}
		
		
	}	

	private void determineCacheType() {
		String jndiName = getCacheJndiName();
		if (jndiName != null && jndiName.trim().length() != 0) {
			cacheType = CACHE_TYPE.USE_JNDI;
		} else if (this.getHotRodClientPropertiesFile() != null) {
			cacheType = CACHE_TYPE.REMOTE_HOT_ROD_PROPERTIES;
		} else if (this.getRemoteServerList() != null
				&& !this.getRemoteServerList().isEmpty()) {
			cacheType = CACHE_TYPE.REMOTE_SERVER_LISTS;
		}
	}

	protected abstract RemoteCacheManager createRemoteCacheWrapperFromProperties(
			ClassLoader classLoader) throws ResourceException;
	
	protected abstract RemoteCacheManager createRemoteCacheWrapperFromServerList(
			ClassLoader classLoader) throws ResourceException;

	
	private RemoteCacheManager createRemoteCacheWrapperFromJNDI(
			String jndiName, ClassLoader classLoader) throws ResourceException {

		Object cache = null;
		try {
			Context context = new InitialContext();
			cache = context.lookup(jndiName);
		} catch (Exception err) {
			if (err instanceof RuntimeException)
				throw (RuntimeException) err;
			throw new ResourceException(err);
		}

		if (cache == null) {
			throw new ResourceException(
					InfinispanPlugin.Util
							.getString(
									"InfinispanManagedConnectionFactory.unableToFindCacheUsingJNDI", jndiName)); //$NON-NLS-1$
		}
		
		
		if (cache instanceof RemoteCacheManager) {
			LogManager.logInfo(LogConstants.CTX_CONNECTOR,
				"=== Using RemoteCacheManager (loaded vi JNDI) ==="); //$NON-NLS-1$

			return (RemoteCacheManager) cacheContainer;
		}

		throw new ResourceException(
			InfinispanPlugin.Util
					.getString(
							"InfinispanManagedConnectionFactory.JNDInotInstanceOfRemoteCacheManager", cacheContainer.getClass().getName())); //$NON-NLS-1$


	}
	
	protected void registerMarshallers(SerializationContext ctx, ClassLoader cl) throws ResourceException {

		try {
			ctx.registerProtofile(cl.getResourceAsStream(getProtobinFile()));
			@SuppressWarnings("rawtypes")
			Map<Class, BaseMarshaller> ml = getMessageMarshallerList();
			Iterator it = ml.keySet().iterator();
			while (it.hasNext()) {
				Class c = (Class) it.next();
				BaseMarshaller m = ml.get(c);
				ctx.registerMarshaller(c, m);
			}

		} catch (IOException e) {
			throw new ResourceException(e);
		} catch (DescriptorValidationException e) {
			throw new ResourceException(e);
		} 
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+  (protobinFile.hashCode());
		result = prime
				* result
				+ ((remoteServerList == null) ? 0 : remoteServerList.hashCode());
		result = prime
				* result
				+ ((hotrodClientPropertiesFile == null) ? 0
						: hotrodClientPropertiesFile.hashCode());
		result = prime * result
				+ ((cacheJndiName == null) ? 0 : cacheJndiName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		AbstractInfinispanManagedConnectionFactory other = (AbstractInfinispanManagedConnectionFactory) obj;

		if (!checkEquals(this.remoteServerList, other.remoteServerList)) {
			return false;
		}
		if (!checkEquals(this.hotrodClientPropertiesFile,
				other.hotrodClientPropertiesFile)) {
			return false;
		}
		if (!checkEquals(this.cacheJndiName, other.cacheJndiName)) {
			return false;
		}
		return false;

	}

	public void cleanUp() {

		messageMarshallerList = null;
		typeMap = null;
		cacheContainer = null;
		pkMap = null;
		cl = null;

	}
}
