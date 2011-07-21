package com.dynamo.cr.editor.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.dynamo.cr.editor.Activator;


/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 *
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
	    store.setDefault(PreferenceConstants.P_SERVER_URI, "http://cr.defold.se:9998");
	    store.setDefault(PreferenceConstants.P_SOCKS_PROXY_PORT, 1080);
	    store.setDefault(PreferenceConstants.P_DOWNLOAD_APPLICATION, true);
	}
}
