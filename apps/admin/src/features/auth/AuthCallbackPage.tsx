import { useEffect,useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../app/auth/AuthContext';
export function AuthCallbackPage(){const {completeCallback}=useAuth();const navigate=useNavigate();const[error,setError]=useState('');useEffect(()=>{void completeCallback().then(()=>navigate('/dashboard',{replace:true})).catch(()=>setError('The secure sign-in callback could not be completed. Please return to login.'));},[completeCallback,navigate]);return <main className="centered-page"><section className="card auth-card" role="status"><span className="eyebrow">Secure sign in</span><h1>{error?'Sign in failed':'Completing sign in…'}</h1>{error&&<p role="alert" className="error">{error}</p>}</section></main>}
