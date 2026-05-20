import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { clearTokens, getAccessToken, getRefreshToken, refreshAccessToken, setTokenRefreshCallback, setTokens } from '../api/axios';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [isLoading, setIsLoading] = useState(true);
  const [tokenVersion, setTokenVersion] = useState(0);

  const updateSession = useCallback((access, refresh, userData = user) => {
    if (!access || !refresh) return;
    sessionStorage.setItem('ms_session', JSON.stringify({
      accessToken: access,
      refreshToken: refresh,
      user: userData,
    }));
    setTokens(access, refresh);
    setTokenVersion((value) => value + 1);
  }, [user]);

  useEffect(() => {
    let cancelled = false;

    const restoreSession = async () => {
    const savedSession = sessionStorage.getItem('ms_session');
    if (savedSession) {
      try {
        const { accessToken, refreshToken, user: savedUser } = JSON.parse(savedSession);
        setTokens(accessToken, refreshToken);
        const refreshed = await refreshAccessToken();
        if (cancelled) return;
        sessionStorage.setItem('ms_session', JSON.stringify({
          accessToken: refreshed.accessToken,
          refreshToken: refreshed.refreshToken,
          user: savedUser,
        }));
        setUser(savedUser);
        setIsAuthenticated(true);
      } catch {
        clearTokens();
        sessionStorage.removeItem('ms_session');
      }
    }
      if (!cancelled) setIsLoading(false);
    };

    restoreSession();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    setTokenRefreshCallback((newAccess, newRefresh) => updateSession(newAccess, newRefresh));
    return () => setTokenRefreshCallback(null);
  }, [updateSession]);

  const login = useCallback((authData, userProfile) => {
    const userData = {
      userId: authData.userId,
      id: userProfile?.id || authData.userId,
      email: authData.email,
      name: userProfile?.name || authData.email,
      timezone: userProfile?.timezone || 'UTC',
      notificationPreference: userProfile?.notificationPreference || 'BOTH',
    };
    updateSession(authData.accessToken, authData.refreshToken, userData);
    setUser(userData);
    setIsAuthenticated(true);
  }, [updateSession]);

  const updateUser = useCallback((updatedProfile) => {
    setUser((current) => {
      const newUser = { ...current, ...updatedProfile, userId: updatedProfile.id || current?.userId };
      const savedSession = sessionStorage.getItem('ms_session');
      if (savedSession) {
        const session = JSON.parse(savedSession);
        sessionStorage.setItem('ms_session', JSON.stringify({ ...session, user: newUser }));
      }
      return newUser;
    });
  }, []);

  const logout = useCallback(() => {
    clearTokens();
    sessionStorage.removeItem('ms_session');
    setUser(null);
    setIsAuthenticated(false);
    setTokenVersion((value) => value + 1);
  }, []);

  const value = useMemo(() => ({
    user,
    isAuthenticated,
    isLoading,
    accessToken: getAccessToken(),
    refreshToken: getRefreshToken(),
    tokenVersion,
    login,
    logout,
    updateUser,
  }), [user, isAuthenticated, isLoading, tokenVersion, login, logout, updateUser]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
};
