import React, {useEffect, useState} from 'react';
import axios from 'axios';

const LoginHandler: React.FC = () => {

  const [error, setError] = useState<String | null>(null)

  const onLoad = () => {
    axios.get('/api/invite').then(resp => {
      const redirect = resp.data;
      window.location.replace(redirect);
    }).catch(e => {
      setError("Could not retrieve invite URL")
    })
  }

  useEffect(onLoad, []);

  if (error) {
    return <React.Fragment>
      <h1>Error</h1>
      <p>
        There was an error redirecting you. Please try again
      </p>
      <pre>{error}</pre>
    </React.Fragment>
  }
  return <h1>Please wait...</h1>
};

export default LoginHandler;