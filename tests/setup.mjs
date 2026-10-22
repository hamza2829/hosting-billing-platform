import {randomBytes} from 'node:crypto';
import {existsSync,writeFileSync} from 'node:fs';
if(!existsSync('.env'))writeFileSync('.env',['API_KEY','DATABASE_PASSWORD'].map(k=>k+'='+randomBytes(24).toString('hex')).join('\n')+'\n');
console.log('Local .env prepared without printing secret values.');
