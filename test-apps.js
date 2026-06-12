// Simple test script to verify frontend apps are responding
const http = require('http');

const apps = [
  { name: 'Driver App', url: 'http://localhost:3002' },
  { name: 'Rider App', url: 'http://localhost:5173' },
  { name: 'Admin App', url: 'http://localhost:3000' }
];

function testApp(appUrl) {
  return new Promise((resolve) => {
    http.get(appUrl, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        const hasLoginElements = data.includes('LoginPage') || data.includes('RideShare') || data.includes('Sign In');
        const hasDesignColors = data.includes('#00FF88') || data.includes('00D9FF') || data.includes('1A1F2E');
        resolve({
          status: res.statusCode,
          hasLoginElements,
          hasDesignColors,
          size: data.length
        });
      });
    }).on('error', () => resolve({ status: 'ERROR', error: true }));
  });
}

async function runTests() {
  console.log('Testing Frontend Applications...\n');

  for (const app of apps) {
    const result = await testApp(app.url);
    console.log(`${app.name}:`);
    console.log(`  Status: ${result.status}`);
    console.log(`  HTML Size: ${result.size} bytes`);
    console.log(`  Has Login Elements: ${result.hasLoginElements || 'N/A'}`);
    console.log(`  Has Design Colors: ${result.hasDesignColors || 'N/A'}`);
    console.log('');
  }
}

runTests();
