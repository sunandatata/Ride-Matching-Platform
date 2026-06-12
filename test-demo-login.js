// Test Demo Login functionality
const puppeteer = require('puppeteer');

const apps = [
  { name: 'Driver App', url: 'http://localhost:3002' },
  { name: 'Rider App', url: 'http://localhost:5173' },
  { name: 'Admin App', url: 'http://localhost:3000' }
];

async function testDemoLogin(appUrl, appName) {
  let browser;
  try {
    browser = await puppeteer.launch({ headless: 'new' });
    const page = await browser.newPage();

    // Set up console message listener
    page.on('console', msg => {
      if (msg.type() === 'error') console.log(`  Browser Error: ${msg.text()}`);
    });

    console.log(`\nTesting ${appName}...`);
    console.log(`URL: ${appUrl}`);

    // Navigate to app
    await page.goto(appUrl, { waitUntil: 'networkidle2', timeout: 10000 });
    console.log('✓ Page loaded');

    // Check for Demo Login button by looking for text content
    const buttonFound = await page.evaluate(() => {
      const buttons = Array.from(document.querySelectorAll('button'));
      return buttons.some(btn => btn.textContent.includes('Demo Login'));
    });

    if (buttonFound) {
      console.log('✓ Demo Login button found');

      // Click the button
      await page.evaluate(() => {
        const buttons = Array.from(document.querySelectorAll('button'));
        const demoBtn = buttons.find(btn => btn.textContent.includes('Demo Login'));
        if (demoBtn) demoBtn.click();
      });
      console.log('✓ Demo Login button clicked');

      // Wait for navigation
      await page.waitForNavigation({ waitUntil: 'networkidle0', timeout: 5000 }).catch(() => {});

      // Get localStorage values
      const localStorage = await page.evaluate(() => {
        return {
          access_token: window.localStorage.getItem('access_token'),
          refresh_token: window.localStorage.getItem('refresh_token'),
          user: window.localStorage.getItem('user')
        };
      });

      console.log('✓ localStorage values:');
      if (localStorage.access_token) {
        console.log(`  - access_token: ${localStorage.access_token.substring(0, 20)}...`);
      } else {
        console.log('  - access_token: NOT SET ❌');
      }

      if (localStorage.refresh_token) {
        console.log(`  - refresh_token: ${localStorage.refresh_token.substring(0, 20)}...`);
      } else {
        console.log('  - refresh_token: NOT SET ❌');
      }

      if (localStorage.user) {
        try {
          const user = JSON.parse(localStorage.user);
          console.log(`  - user: ${user.name} (${user.id})`);
        } catch (e) {
          console.log('  - user: INVALID JSON ❌');
        }
      } else {
        console.log('  - user: NOT SET ❌');
      }

      // Check current URL
      const currentUrl = page.url();
      console.log(`✓ Current URL: ${currentUrl}`);

      return { success: true, localStorage };
    } else {
      console.log('❌ Demo Login button NOT found');
      return { success: false, error: 'Demo Login button not found' };
    }
  } catch (error) {
    console.log(`❌ Error: ${error.message}`);
    return { success: false, error: error.message };
  } finally {
    if (browser) await browser.close();
  }
}

async function runTests() {
  console.log('='.repeat(60));
  console.log('DEMO LOGIN FUNCTIONALITY TEST');
  console.log('='.repeat(60));

  const results = [];
  for (const app of apps) {
    const result = await testDemoLogin(app.url, app.name);
    results.push({ app: app.name, ...result });
  }

  console.log('\n' + '='.repeat(60));
  console.log('TEST SUMMARY');
  console.log('='.repeat(60));

  results.forEach(r => {
    const status = r.success ? '✓ PASS' : '✗ FAIL';
    console.log(`${r.app}: ${status}`);
  });
}

runTests().catch(console.error);
