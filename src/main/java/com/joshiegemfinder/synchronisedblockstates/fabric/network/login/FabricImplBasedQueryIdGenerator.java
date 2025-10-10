package com.joshiegemfinder.synchronisedblockstates.fabric.network.login;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.IntSupplier;

import com.joshiegemfinder.synchronisedblockstates.common.network.login.QueryIdGenerator;

import net.fabricmc.fabric.impl.networking.server.ServerLoginNetworkAddon;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;

// exists to be wrapped with a try ... catch for ClassDefNotFoundError & ClassNotFoundException & co.
public record FabricImplBasedQueryIdGenerator(IntSupplier factory) implements QueryIdGenerator {

	public static IntSupplier getQueryFactory(ServerLoginPacketListenerImpl packetListener) throws ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException {
		Class<?> classQueryIdFactory = Class.forName("net.fabricmc.fabric.impl.networking.server.QueryIdFactory");
		
		Method nextIdMethod = classQueryIdFactory.getDeclaredMethod("nextId");
		nextIdMethod.setAccessible(true);
		
		ServerLoginNetworkAddon addon = ServerNetworkingImpl.getAddon(packetListener);
		
		Field queryIdFactoryField = addon.getClass().getDeclaredField("queryIdFactory");
		queryIdFactoryField.setAccessible(true);
		
		Object queryIdFactory = queryIdFactoryField.get(addon);
		
		// TODO re-add QueryIdGenerator fallback once testing is done
		return () -> {
			try {
				return ((Integer)nextIdMethod.invoke(queryIdFactory)).intValue();
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				throw new RuntimeException(e);
			}
		};
	}
	
	public FabricImplBasedQueryIdGenerator(ServerLoginPacketListenerImpl packetListener) throws ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchMethodException {
		this(getQueryFactory(packetListener));
	}

	@Override
	public int nextQueryId() {
		return factory.getAsInt();
	}
	
}
