# JaldiQ ⏳📱
**Digitizing Local Queues for a Wait-Free Experience**

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)
![NodeJS](https://img.shields.io/badge/Node.js-43853D?style=for-the-badge&logo=node.js&logoColor=white)
![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)

[cite_start]JaldiQ is a local service queue manager that takes the pain out of waiting[cite: 37]. [cite_start]Instead of standing in long, tiring lines, customers can join a virtual queue from their phone and wait wherever they are comfortable[cite: 38]. 

[cite_start]Whether it's a local barber, a busy clinic, or a repair center, JaldiQ provides a universal, single-app solution to manage crowds and give people their valuable time back[cite: 15, 39, 40].

---

## 🛑 The Problem
Traditional queuing is broken:
* [cite_start]**Physical Exhaustion:** People stand for hours in lines at clinics, salons, and repair shops[cite: 5].
* [cite_start]**Time Anxiety:** Customers don't know if the wait is 10 minutes or 1 hour[cite: 6].
* [cite_start]**Fear of Leaving:** Step away for a moment, and you lose your spot[cite: 7].
* [cite_start]**Shopkeeper Stress:** Owners are constantly distracted by people asking, "When is my turn?"[cite: 8].
* [cite_start]**Blind Visits:** Traveling to a shop only to find it overcrowded wastes time[cite: 9].

## ✨ Key Features

### For Customers 🧑‍🤝‍🧑
* [cite_start]**Smart Virtual Tokens:** Instead of a paper slip, get a digital token showing exactly how many people are ahead of you and your estimated wait time[cite: 11].
* [cite_start]**Remote Queuing:** Join the line from home or while doing other errands[cite: 14].
* [cite_start]**Busy-Time Indicator:** A traffic-light system (Green, Yellow, Red) shows current crowd levels before you even leave home[cite: 25].
* **One Token Rule & Leave Queue:** Fair queueing logic ensures one active token per person, with the flexibility to cancel if plans change.

### For Shop Owners 🏪
* **Low-Tech Dashboard:** Designed for non-technical owners. [cite_start]Manage the queue with massive, simple buttons: **Next**, **Pause**, and **Close**[cite: 12].
* [cite_start]**No Extra Hardware:** No printers or tablets needed—it runs completely on a standard Android phone[cite: 13].
* **Real-Time Customer List:** See exactly who is waiting in line with an instantly updating dashboard.

### Smart System Features 🧠
* **Missed-Turn Protection (Grace Window):** If a user is stuck in traffic, they aren't immediately canceled. [cite_start]The system creates a "Grace Window" allowing them to claim a slightly delayed spot[cite: 26].
* **WhatsApp/SMS Updates (Upcoming):** Critical for elderly users who don't want to use an app. [cite_start]They receive simple text updates (e.g., "2 people left")[cite: 27].

---

## 🛠️ Technology Stack
* [cite_start]**Frontend:** Native Android using **Kotlin** & **Jetpack Compose** for a modern, reactive UI[cite: 32].
* **Architecture:** MVVM (Model-View-ViewModel) + Clean Architecture + Hilt (Dependency Injection).
* [cite_start]**Backend:** **Node.js / Express** for handling complex queue logic, cron jobs, and webhooks[cite: 33].
* [cite_start]**Database:** **Firebase Realtime Database** for instant, millisecond-latency status updates across all devices[cite: 34].
* **Authentication:** Firebase Auth (Role-based: Customers vs. Shop Owners).
